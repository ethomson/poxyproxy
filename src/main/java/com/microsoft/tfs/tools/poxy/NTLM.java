package com.microsoft.tfs.tools.poxy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Provider;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.microsoft.tfs.tools.poxy.logger.Logger;

public class NTLM
{
	private final static Logger logger = Logger.getLogger(NTLM.class);

	private final static Provider MD4_PROVIDER = new MD4Provider();

	/* LM authentication is (even more) insecure; it should not be used. */
	private final static boolean allowLM = false;

	public static NTLMMessage.Type2Message createChallenge(NTLMMessage.Type1Message negotiate) throws Exception
	{
		int flags = 0;

		if ((negotiate.getFlags() & NTLMMessage.FLAG_NEGOTIATE_UNICODE) == NTLMMessage.FLAG_NEGOTIATE_UNICODE)
		{
			flags |= NTLMMessage.FLAG_NEGOTIATE_UNICODE;
		}
		else if ((negotiate.getFlags() & NTLMMessage.FLAG_NEGOTIATE_OEM) == NTLMMessage.FLAG_NEGOTIATE_OEM)
		{
			flags |= NTLMMessage.FLAG_NEGOTIATE_OEM;
		}
		else
		{
			throw new Exception("Unknown charset");
		}

		final String hostname = Utils.getHostname();

		flags |= (negotiate.getFlags() & NTLMMessage.FLAG_REQUEST_TARGET);
		flags |= (negotiate.getFlags() & NTLMMessage.FLAG_NEGOTIATE_NTLM);
		flags |= (negotiate.getFlags() & NTLMMessage.FLAG_NEGOTIATE_ALWAYS_SIGN);
		flags |= (negotiate.getFlags() & NTLMMessage.FLAG_NEGOTIATE_EXTENDED_SESSIONSECURITY);
		flags |= (negotiate.getFlags() & NTLMMessage.FLAG_NEGOTIATE_128);
		flags |= (negotiate.getFlags() & NTLMMessage.FLAG_NEGOTIATE_56);

		flags |= NTLMMessage.FLAG_TARGET_TYPE_SERVER;
		flags |= NTLMMessage.FLAG_NEGOTIATE_TARGET_INFO;
		final NTLMMessage.TargetInformation targetInfo = new NTLMMessage.TargetInformation(hostname, hostname, hostname, hostname, new Date());

		flags |= (negotiate.getFlags() & NTLMMessage.FLAG_NEGOTIATE_VERSION);
		final NTLMMessage.Version version = new NTLMMessage.Version((byte)0, (byte)5, (short)42);

		final byte[] challenge = new byte[8];
		ThreadLocalRandom.current().nextBytes(challenge);

		return new NTLMMessage.Type2Message(flags, challenge, hostname, targetInfo, version);
	}

	public static boolean verifyResponse(String username, String domain, String password, NTLMMessage.Type2Message challenge, NTLMMessage.Type3Message response) throws Exception
	{
		// If we doesn't care about the domain, just use the web user's
		domain = domain != null ? domain : response.getDomain();

		return (verifyNTLM2Response(username, domain, password, challenge, response));
	}

	private static boolean verifyLMResponse(String username, String domain, String password, NTLMMessage.Type2Message challenge, NTLMMessage.Type3Message response) throws Exception
	{
		byte[] expected = createLMResponse(lmHash(password), challenge.getChallenge());

		return arrayEquals(expected, response.getLMResponse());
	}

	private static boolean verifyNTLMResponse(String username, String domain, String password, NTLMMessage.Type2Message challenge, NTLMMessage.Type3Message response) throws Exception
	{
		byte[] expected = createLMResponse(ntlmHash(password), challenge.getChallenge());

		return arrayEquals(expected, response.getNTLMResponse());
	}

	private static boolean verifyLM2Response(String username, String domain, String password, NTLMMessage.Type2Message challenge, NTLMMessage.Type3Message response) throws Exception
	{
		assert(username != null && username.length() > 0);
		assert(password != null && password.length() > 0);

		username = username.toUpperCase();
		domain = domain.toUpperCase();

		if (response.getLMResponse() == null)
			throw new Exception("Invalid NTLM2 response: no LM response data");

		if (response.getLMResponse().length != 24)
			return false;

		// The LM2 response is a hash of both the challenge and a client nonce, concatenated
		// with the client nonce
		byte[] lm2Hash = new byte[16];
		byte[] clientNonce = new byte[8];

		System.arraycopy(response.getLMResponse(), 0, lm2Hash, 0, 16);
		System.arraycopy(response.getLMResponse(), 16, clientNonce, 0, 8);

		// Compute the LM2 hash ourselves.  This is the HMAC-MD5 of the
		// server challenge concatenated with the client nonce, using the
		// ntlm2 hash as a key
		byte[] challenges = new byte[challenge.getChallenge().length + clientNonce.length];
		System.arraycopy(challenge.getChallenge(), 0, challenges, 0, challenge.getChallenge().length);
		System.arraycopy(clientNonce, 0, challenges, challenge.getChallenge().length, clientNonce.length);

		final Mac md5 = Mac.getInstance("HmacMD5"); //$NON-NLS-1$
		md5.init(new SecretKeySpec(ntlm2Hash(username, password, domain), "HmacMD5")); //$NON-NLS-1$
		byte[] expectedHash = md5.doFinal(challenges);

		return arrayEquals(expectedHash, lm2Hash);
	}

	private static boolean verifyNTLM2Response(String username, String domain, String password, NTLMMessage.Type2Message challenge, NTLMMessage.Type3Message response) throws Exception
	{
		assert(username != null && username.length() > 0);
		assert(password != null && password.length() > 0);

		int targetInfoLen = 0;

		username = username.toUpperCase();
		domain = domain.toUpperCase();

		// Skip if this is not the credentials presented by the client
		if (
				!username.equals(response.getUsername().toUpperCase()) ||
				!domain.equals(response.getDomain().toUpperCase()))
		{
			return false;
		}

		if ((challenge.getFlags() & NTLMMessage.FLAG_NEGOTIATE_TARGET_INFO) == NTLMMessage.FLAG_NEGOTIATE_TARGET_INFO)
		{
			if (challenge.getTargetInformation() == null)
			{
				throw new Exception("Invalid NTLM2 challenge: no target information");
			}

			targetInfoLen = challenge.getTargetInformation().length;
		}

		// Get the NTLM2 response
		if (response.getNTLMResponse() == null)
			throw new Exception("Invalid NTLM response: no NTLM2 response data");

		// The NTLM2 response must be the size of the server's target information
		// plus 32 bytes of additional response data, plus 16 bytes of hash code
		if (response.getNTLMResponse().length < targetInfoLen + 48)
			return false;

		byte[] responseHashData = new byte[16];
		byte[] responseData = new byte[response.getNTLMResponse().length - 16];

		System.arraycopy(response.getNTLMResponse(), 0, responseHashData, 0, 16);
		System.arraycopy(response.getNTLMResponse(), 16, responseData, 0, (response.getNTLMResponse().length - 16));

		//
		// Ensure the NTLM response is valid
		//

		// Ensure the NTLM Response header is valid (0x01010000)
		if (responseData[0] != 1 || responseData[1] != 1 || responseData[2] != 0 || responseData[3] != 0)
			return false;

		// Get the NTLM2 hash
		byte[] ntlmHash = ntlm2Hash(username, password, domain);

		//
		// Compute the response hash
		// Create an HMAC-MD5 hash with the NTLM 2 hash (above) as the key
		// Hash the server's challenge concatenated with the client's response
		//

		byte[] challengeBlob = new byte[challenge.getChallenge().length + responseData.length];
		System.arraycopy(challenge.getChallenge(), 0, challengeBlob, 0, challenge.getChallenge().length);
		System.arraycopy(responseData, 0, challengeBlob, challenge.getChallenge().length, responseData.length);

		// Compute the responseHash - the HMAC-MD5 of the ntlmResponseData,
		// using the ntlm2 hash as the key
		final Mac mac = Mac.getInstance("HmacMD5"); //$NON-NLS-1$
		mac.init(new SecretKeySpec(ntlmHash, "HmacMD5")); //$NON-NLS-1$
		byte[] expectedResponseHashData = mac.doFinal(challengeBlob);

		// Ensure the hash is what the client delivered
		if(! arrayEquals(expectedResponseHashData, responseHashData))
			return false;

		return true;
	}

	private static boolean verifyNTLM2SessionResponse(String username, String domain, String password, NTLMMessage.Type2Message challenge, NTLMMessage.Type3Message response) throws Exception
	{
		// Create the session hash, the MD5 hash of the server challenge
		// concatenated with the client nonce.  (The client nonce in this
		// instance is supplied in the LMResponse field, in the first 8 bytes.)
		byte[] sessionData = new byte[16];
		System.arraycopy(challenge.getChallenge(), 0, sessionData, 0, 8);
		System.arraycopy(response.getLMResponse(), 0, sessionData, challenge.getChallenge().length, 8);

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		byte[] sessionHash = md5.digest(sessionData);

		// Now hash the NTLM key in the old LM response manner,
		// using the sessionHash as the challenge data
		byte[] expectedResponse = createLMResponse(ntlmHash(password), sessionHash);

		return arrayEquals(expectedResponse, response.getNTLMResponse());
	}

	private static byte[] createLMResponse(byte[] hash, byte[] challenge) throws Exception
	{
		// create three 7 byte keys from the hash
		byte[][] password = new byte[3][];
		password[0] = new byte[7];
		password[1] = new byte[7];
		password[2] = new byte[7];

		for (int i = 0; i < 7; i++)
		{
			password[0][i] = (hash.length > i) ? hash[i] : (byte)0;
			password[1][i] = (hash.length > 7 + i) ? hash[7 + i] : (byte)0;
			password[2][i] = (hash.length > 14 + i) ? hash[14 + i] : (byte)0;
		}

		// hash the magic with the two halves of the password, then
		// concatenate
		byte[][] crypt = new byte[3][];

		for (int i = 0; i < 3; i++)
		{
			final Cipher desCipher = Cipher.getInstance("DES/ECB/NoPadding");

			// encrypt challenge w/ first third of hash
			desCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(desKey(password[i]), "DES"));
			crypt[i] = desCipher.doFinal(challenge);
		}

		// concatenate triples
		byte[] lmResponse = new byte[24];
		for (int i = 0; i < 8; i++)
		{
			lmResponse[i] = crypt[0][i];
			lmResponse[8 + i] = crypt[1][i];
			lmResponse[16 + i] = crypt[2][i];
		}

		return lmResponse;
	}

	private static byte[] desKey(byte[] ascii)
	{
		byte[] keyData = new byte[8];

		keyData[0] = (byte)((ascii[0] >> 1) & 0xFF);
		keyData[1] = (byte)((((ascii[0] & 0x01) << 6) | (((ascii[1] & 0xFF) >> 2) & 0xFF)) & 0xFF);
		keyData[2] = (byte)((((ascii[1] & 0x03) << 5) | (((ascii[2] & 0xFF) >> 3) & 0xFF)) & 0xff);
		keyData[3] = (byte)((((ascii[2] & 0x07) << 4) | (((ascii[3] & 0xff) >> 4) & 0xFF)) & 0xFF);
		keyData[4] = (byte)((((ascii[3] & 0x0F) << 3) | (((ascii[4] & 0xFF) >> 5) & 0xFF)) & 0xFF);
		keyData[5] = (byte)((((ascii[4] & 0x1F) << 2) | (((ascii[5] & 0xFF) >> 6) & 0xFF)) & 0xFF);
		keyData[6] = (byte)((((ascii[5] & 0x3F) << 1) | (((ascii[6] & 0xFF) >> 7) & 0xFF)) & 0xFF);
		keyData[7] = (byte)(ascii[6] & 0x7F);

		for (int i = 0; i < keyData.length; i++)
		{
			keyData[i] = (byte)(keyData[i] << 1);
		}

		return keyData;
	}

	private static byte[] lmHash(String password) throws Exception
	{
		// LM password "magic"
		byte[] magic =
			{
					(byte) 0x4B, (byte) 0x47, (byte) 0x53, (byte) 0x21, (byte) 0x40, (byte) 0x23, (byte) 0x24, (byte) 0x25
			};

		byte[] passwordBytes = password.toUpperCase().getBytes(StandardCharsets.US_ASCII);
		byte[] password1 = new byte[7];
		byte[] password2 = new byte[7];

		// split the password into two 7 byte arrays (null-padded)
		for (int i = 0; i < 7; i++)
		{
			password1[i] = (passwordBytes.length > i) ? passwordBytes[i] : (byte)0;
			password2[i] = (passwordBytes.length > 7 + i) ? passwordBytes[7 + i] : (byte)0;
		}

		byte[] hashed1 = new byte[8];
		byte[] hashed2 = new byte[8];

		// hash the magic with the two halves of the password, then
		// concatenate
		final Cipher desCipher = Cipher.getInstance("DES/ECB/NoPadding"); //$NON-NLS-1$

		// encrypt magic w/ first half of password
		desCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(desKey(password1), "DES"));
		hashed1 = desCipher.doFinal(magic);

		// encrypt magic w/ second half of password
		desCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(desKey(password2), "DES"));
		hashed2 = desCipher.doFinal(magic);

		// concatenate the top and bottom
		byte[] lmHash = new byte[16];
		for (int i = 0; i < 8; i++)
		{
			lmHash[i] = (hashed1.length > i) ? hashed1[i] : (byte)0;
			lmHash[8 + i] = (hashed2.length > i) ? hashed2[i] : (byte)0;
		}

		return lmHash;
	}

	/// <summary>
	/// NTLM hash is computed as the MD4 hash of the Unicode-16 representation of the password.
	/// </summary>
	private static byte[] ntlmHash(String password) throws Exception
	{
		final MessageDigest md4 = MessageDigest.getInstance("MD4", MD4_PROVIDER);
		return md4.digest(password.getBytes(StandardCharsets.UTF_16LE));
	}

	/// <summary>
	/// NTLM2 hash is computed as the HMAC-MD5 of the Unicode-16 representation of
	/// the username and domain (concatenated), using the NTLM hash (above) as the key
	/// </summary>
	private static byte[] ntlm2Hash(String username, String password, String domain) throws Exception
	{
		byte[] ntlmHash = ntlmHash(password);

		// we need the username and domain concatenated
		String usernameDomain = username.toUpperCase() + domain.toUpperCase();
		byte[] usernameDomainBytes = usernameDomain.getBytes(StandardCharsets.UTF_16LE);

		// ntlm2 hash is created by running HMAC-MD5 on the unicode
		// username and domain (uppercased), with the ntlmHash as a
		// key
		final Mac md5 = Mac.getInstance("HmacMD5"); //$NON-NLS-1$
		md5.init(new SecretKeySpec(ntlmHash, "HmacMD5")); //$NON-NLS-1$
		return md5.doFinal(usernameDomainBytes);
	}

	private static boolean arrayEquals(byte[] one, byte[] two)
	{
		assert(one != null);
		assert(two != null);

		if (one.length != two.length)
			return false;

		return arrayEquals(one, 0, two, 0, one.length);
	}

	private static boolean arrayEquals(byte[] one, int onePos, byte[] two, int twoPos, int len)
	{
		assert(one != null);
		assert(onePos >= 0 && onePos < one.length);
		assert(two != null);
		assert(twoPos >= 0 && twoPos < two.length);
		assert(len >= 0);
		assert(len <= (one.length - onePos) && len <= (two.length - twoPos));

		for (int i = 0; i < len; i++)
		{
			if (one[i + onePos] != two[i + twoPos])
				return false;
		}

		return true;
	}
}

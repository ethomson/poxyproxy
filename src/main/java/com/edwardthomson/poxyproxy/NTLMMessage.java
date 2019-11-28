package com.edwardthomson.poxyproxy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/// <summary>
/// Handy methods for decoding NTLM messages
/// </summary>
public abstract class NTLMMessage
{
	public static final int FLAG_NEGOTIATE_UNICODE = 0x00000001;
	public static final int FLAG_NEGOTIATE_OEM = 0x00000002;
	public static final int FLAG_REQUEST_TARGET = 0x00000004;
	public static final int FLAG_NEGOTIATE_NTLM = 0x00000200;
	public static final int FLAG_NEGOTIATE_ALWAYS_SIGN = 0x00008000;
	public static final int FLAG_TARGET_TYPE_DOMAIN = 0x00010000;
	public static final int FLAG_TARGET_TYPE_SERVER = 0x00020000;
	public static final int FLAG_NEGOTIATE_EXTENDED_SESSIONSECURITY = 0x00080000;
	public static final int FLAG_NEGOTIATE_TARGET_INFO = 0x00800000;
	public static final int FLAG_NEGOTIATE_VERSION = 0x02000000;
	public static final int FLAG_NEGOTIATE_128 = 0x20000000;
	public static final int FLAG_NEGOTIATE_56 = 0x80000000;

	public static final int TARGET_INFO_END = 0;
	public static final int TARGET_INFO_SERVER = 1;
	public static final int TARGET_INFO_DOMAIN = 2;
	public static final int TARGET_INFO_SERVER_DNS = 3;
	public static final int TARGET_INFO_DOMAIN_DNS = 4;
	public static final int TARGET_INFO_TIMESTAMP = 7;

	public static NTLMMessage parse(byte[] message) throws Exception
	{
		assert(message != null);

		int messageType = checkHeader(message);

		if (message.length < 12)
			throw new Exception("Invalid NTLM message");

		messageType = getInt32(message, 8);

		if (messageType == 1)
			return new Type1Message(message);
		else if (messageType == 2)
			return new Type2Message(message);
		else if (messageType == 3)
			return new Type3Message(message);

		throw new Exception("Invalid NTLM message: type " + messageType);
	}

	public abstract int getType();

	public static int checkHeader(byte[] message) throws Exception
	{
		assert(message != null);

		if (message.length < 12)
			throw new Exception("Invalid NTLM message");

		// Check for NTLM message header: "NTLMSSP\0"
		if (
				message[0] != 0x4e || message[1] != 0x54 || message[2] != 0x4c || message[3] != 0x4d ||
				message[4] != 0x53 || message[5] != 0x53 || message[6] != 0x50 || message[7] != 0x00
				)
			throw new Exception("Invalid NTLM message");

		return getInt32(message, 8);
	}

	protected static short getInt16(byte[] message, int position)
	{
		assert(message.length >= (position + 2));

		return (short)(
				(message[position] & 0xFF) |
				((message[position + 1] & 0xFF) << 8));
	}

	protected static int getInt32(byte[] message, int position)
	{
		assert(message.length >= (position + 4));

		return (message[position] & 0xFF) |
				((message[position + 1] & 0xFF) << 8) |
				((message[position + 2] & 0xFF) << 16) |
				((message[position + 3] & 0xFF) << 24);
	}

	protected static byte[] getBytes(byte[] message, int position, int length)
	{
		assert(position >= 0);
		assert(length == 0 || message.length >= (position + length));

		if (length == 0)
			return new byte[0];

		byte[] result = new byte[length];

		System.arraycopy(message, position, result, 0, length);
		return result;
	}

	private static void addBytes(final byte[] buf, int pos, final byte[] bytes)
	{
		if (pos < 0 || buf.length < pos + bytes.length)
		{
			throw new ArrayIndexOutOfBoundsException();
		}

		for (int i = 0; i < bytes.length; i++)
		{
			buf[pos++] = bytes[i];
		}
	}

	private static void addByte(final byte[] buf, int pos, final byte b)
	{
		if (pos < 0 || buf.length < pos + 1)
		{
			throw new ArrayIndexOutOfBoundsException();
		}

		buf[pos++] = b;
	}

	private static void addShort(final byte[] buf, final int pos, final int shortnum)
	{
		if (pos < 0 || buf.length < pos + 2)
		{
			throw new ArrayIndexOutOfBoundsException();
		}

		addByte(buf, pos, (byte) ((shortnum & 0x000000FF)));
		addByte(buf, pos + 1, (byte) ((shortnum & 0x0000FF00) >> 8));
	}

	private static void addInt(final byte[] buf, final int pos, final int intnum)
	{
		if (pos < 0 || buf.length < pos + 4)
		{
			throw new ArrayIndexOutOfBoundsException();
		}

		addByte(buf, pos, (byte) ((intnum & 0x000000FF)));
		addByte(buf, pos + 1, (byte) ((intnum & 0x0000FF00) >> 8));
		addByte(buf, pos + 2, (byte) ((intnum & 0x00FF0000) >> 16));
		addByte(buf, pos + 3, (byte) ((intnum & 0xFF000000) >> 24));
	}

	private static void addLong(final byte[] buf, final int pos, final long longnum)
	{
		if (pos < 0 || buf.length < pos + 4)
		{
			throw new ArrayIndexOutOfBoundsException();
		}

		addByte(buf, pos, (byte) ((longnum & 0x00000000000000FFL)));
		addByte(buf, pos + 1, (byte) ((longnum & 0x000000000000FF00L) >> 8));
		addByte(buf, pos + 2, (byte) ((longnum & 0x0000000000FF0000L) >> 16));
		addByte(buf, pos + 3, (byte) ((longnum & 0x00000000FF000000L) >> 24));
		addByte(buf, pos + 4, (byte) ((longnum & 0x000000FF00000000L) >> 32));
		addByte(buf, pos + 5, (byte) ((longnum & 0x0000FF0000000000L) >> 40));
		addByte(buf, pos + 6, (byte) ((longnum & 0x00FF000000000000L) >> 48));
		addByte(buf, pos + 7, (byte) ((longnum & 0xFF00000000000000L) >> 56));
	}

	static class Type1Message extends NTLMMessage
	{
		private final int flags;

		private final String domain;
		private final String hostname;

		public Type1Message(byte[] message) throws Exception
		{
			assert(message != null);

			if (checkHeader(message) != this.getType())
				throw new Exception("Invalid NTLM type 1 message");

			this.flags = getInt32(message, 12);

			if (message.length > 16)
			{
				short domainLen = getInt16(message, 16);
				short domainPos = getInt16(message, 20);

				this.domain = new String(getBytes(message, domainPos, domainLen), StandardCharsets.US_ASCII);
			}
			else
			{
				this.domain = null;
			}

			if (message.length > 24)
			{
				short hostLen = getInt16(message, 24);
				short hostPos = getInt16(message, 28);

				this.hostname = new String(getBytes(message, hostPos, hostLen), StandardCharsets.US_ASCII);
			}
			else
			{
				this.hostname = null;
			}
		}

		@Override
		public int getType()
		{
			return 1;
		}

		public int getFlags()
		{
			return flags;
		}

		public String getDomain()
		{
			return domain;
		}

		public String getHostname()
		{
			return hostname;
		}
	}

	static class Type2Message extends NTLMMessage
	{
		private byte[] target;

		private final int flags;
		private final byte[] challenge;

		private byte[] context;
		private byte[] targetInformation;
		private byte[] version;

		public Type2Message(byte[] message) throws Exception
		{
			assert(message != null);

			if (checkHeader(message) != this.getType())
				throw new Exception("Invalid NTLM type 2 message");

			short targetNameLen = getInt16(message, 12);
			short targetNamePos = getInt16(message, 16);

			this.target = getBytes(message, targetNamePos, targetNameLen);

			this.flags = getInt32(message, 20);
			this.challenge = getBytes(message, 24, 8);

			// If there's data between the challenge and target name,
			// this is the context and targetInfo
			if (message.length > 48 && targetNamePos >= 48)
			{
				this.context = getBytes(message, 32, 8);

				short targetInfoLen = getInt16(message, 40);
				short targetInfoPos = getInt16(message, 44);

				this.targetInformation = getBytes(message, targetInfoPos, targetInfoLen);
			}
			else
			{
				this.context = null;
				this.targetInformation = null;
			}
		}

		public Type2Message(int flags, byte[] challenge, String target, TargetInformation targetInfo, Version version)
		{
			assert(challenge != null && challenge.length == 8);
			assert(target != null);

			final Charset charset = ((flags & FLAG_NEGOTIATE_UNICODE) == FLAG_NEGOTIATE_UNICODE) ? StandardCharsets.UTF_16LE : StandardCharsets.US_ASCII;

			this.target = target.getBytes(charset);

			this.challenge = new byte[8];
			System.arraycopy(challenge, 0, this.challenge, 0, 8);

			this.context = null;

			if (targetInfo != null)
			{
				this.targetInformation = targetInfo.createTargetInformation(charset);
			}

			if (version != null)
			{
				this.version = version.createVersion();
			}

			this.flags = flags;
		}

		@Override
		public int getType()
		{
			return 2;
		}

		public int getFlags()
		{
			return flags;
		}

		public byte[] getChallenge()
		{
			return challenge;
		}

		public byte[] getTarget()
		{
			return target;
		}

		public byte[] getContext()
		{
			return context;
		}

		public byte[] getTargetInformation()
		{
			return targetInformation;
		}

		public byte[] createMessage()
		{
			int nextOffset = (targetInformation == null) ? 32 : 48;

			final int versionLen = version != null ? version.length : 0;
			final int versionOffset = nextOffset;
			nextOffset += versionLen;

			final int targetLen = target.length;
			final int targetOffset = nextOffset;
			nextOffset += targetLen;

			final int targetInfoLen = targetInformation != null ? targetInformation.length : 0;
			final int targetInfoOffset = nextOffset;
			nextOffset += targetInfoLen;

			final int type2Length = nextOffset;

			byte[] type2 = new byte[type2Length];

			addBytes(type2, 0, "NTLMSSP".getBytes(StandardCharsets.US_ASCII)); // Signature
			addInt(type2, 8, 2); // Type 2 message indicator

			// target name information
			addShort(type2, 12, targetLen); // length
			addShort(type2, 14, targetLen); // length
			addShort(type2, 16, targetOffset); // offset
			addShort(type2, 18, 0); // null

			addInt(type2, 20, flags);

			addBytes(type2, 24, challenge);

			if (targetInformation != null)
			{
				/* 64 bits of reserved space */
				addBytes(type2, 32, new byte[8]);

				addShort(type2, 40, targetInfoLen); // length
				addShort(type2, 42, targetInfoLen); // length
				addShort(type2, 44, targetInfoOffset); // offset
				addShort(type2, 46, 0); // null
			}

			if (version != null)
			{
				addBytes(type2, versionOffset, version);
			}

			if (target != null)
			{
				addBytes(type2, targetOffset, target);
			}

			if (targetInformation != null)
			{
				addBytes(type2, targetInfoOffset, targetInformation);
			}

			return type2;
		}
	}

	static class Type3Message extends NTLMMessage
	{
		private final int flags;

		private final byte[] lmResponse;
		private final byte[] ntlmResponse;
		private final byte[] challenge;

		private final String domain;
		private final String username;
		private final String hostname;

		public Type3Message(byte[] message) throws Exception
		{
			assert(message != null);

			if (checkHeader(message) != this.getType())
				throw new Exception("Invalid NTLM type 3 message");

			short lmResponseLen = getInt16(message, 12);
			short lmResponsePos = getInt16(message, 16);

			short ntlmResponseLen = getInt16(message, 20);
			short ntlmResponsePos = getInt16(message, 24);

			short domainLen = getInt16(message, 28);
			short domainPos = getInt16(message, 32);

			short usernameLen = getInt16(message, 36);
			short usernamePos = getInt16(message, 40);

			short hostnameLen = getInt16(message, 44);
			short hostnamePos = getInt16(message, 48);

			short sessionKeyLen = getInt16(message, 52);
			short sessionKeyPos = getInt16(message, 56);

			flags = getInt32(message, 60);

			lmResponse = getBytes(message, lmResponsePos, lmResponseLen);
			ntlmResponse = getBytes(message, ntlmResponsePos, ntlmResponseLen);

			byte[] domainData = getBytes(message, domainPos, domainLen);
			byte[] usernameData = getBytes(message, usernamePos, usernameLen);
			byte[] hostnameData = getBytes(message, hostnamePos, hostnameLen);

			if ((flags & FLAG_NEGOTIATE_UNICODE) == FLAG_NEGOTIATE_UNICODE)
			{
				domain = new String(domainData, StandardCharsets.UTF_16LE);
				username = new String(usernameData, StandardCharsets.UTF_16LE);
				hostname = new String(hostnameData, StandardCharsets.UTF_16LE);
			}
			else
			{
				domain = new String(domainData, StandardCharsets.US_ASCII);
				username = new String(usernameData, StandardCharsets.US_ASCII);
				hostname = new String(hostnameData, StandardCharsets.US_ASCII);
			}

			/* Parse the NTLM response */
			byte[] ntlmHash = getBytes(ntlmResponse, 0, 16);
			byte[] signature = getBytes(ntlmResponse, 16, 4);
			byte[] unused = getBytes(ntlmResponse, 20, 4);
			byte[] timestamp = getBytes(ntlmResponse, 24, 8);

			if (signature[0] != 1 || signature[1] != 1 || signature[2] != 0 || signature[3] != 0)
			{
				throw new Exception("Invalid NTLM2 response");
			}

			if (unused[0] != 0 || unused[1] != 0 || unused[2] != 0 || unused[3] != 0)
			{
				throw new Exception("Invalid NTLM2 response");
			}

			challenge = getBytes(ntlmResponse, 32, 8);
		}

		@Override
		public int getType()
		{
			return 3;
		}

		public int getFlags()
		{
			return flags;
		}

		public String getDomain()
		{
			return domain;
		}

		public String getUsername()
		{
			return username;
		}

		public String getHostname()
		{
			return hostname;
		}

		public byte[] getLMResponse()
		{
			return lmResponse;
		}

		public byte[] getNTLMResponse()
		{
			return ntlmResponse;
		}

		public byte[] getChallenge()
		{
			return challenge;
		}
	}

	static class TargetInformation
	{
		private String domain;
		private String server;
		private String dnsDomain;
		private String dnsServer;
		private Date timestamp;

		public TargetInformation(String domain, String server, String dnsDomain, String dnsServer, Date timestamp)
		{
			this.domain = domain;
			this.server = server;
			this.dnsDomain = dnsDomain;
			this.dnsServer = dnsServer;
			this.timestamp = timestamp;
		}

		public byte[] createTargetInformation(Charset charset)
		{
			byte[] domainName = domain != null ? createTargetInformationName(TARGET_INFO_DOMAIN, domain, charset) : new byte[0];
			byte[] serverName = server != null ? createTargetInformationName(TARGET_INFO_SERVER, server, charset) : new byte[0];
			byte[] dnsDomainName = dnsDomain != null ? createTargetInformationName(TARGET_INFO_DOMAIN_DNS, dnsDomain, charset) : new byte[0];
			byte[] dnsServerName = dnsServer != null ? createTargetInformationName(TARGET_INFO_SERVER_DNS, dnsServer, charset) : new byte[0];
			byte[] timestampValue = timestamp != null ? createTargetInformationTimestamp(timestamp) : new byte[0];
			byte[] end = createTargetInformationEnd();

			byte[] targetInfo = new byte[domainName.length + serverName.length + dnsDomainName.length + dnsServerName.length + timestampValue.length + end.length];

			int nextOffset = 0;

			addBytes(targetInfo, nextOffset, domainName);
			nextOffset += domainName.length;

			addBytes(targetInfo, nextOffset, serverName);
			nextOffset += serverName.length;

			addBytes(targetInfo, nextOffset, dnsDomainName);
			nextOffset += dnsDomainName.length;

			addBytes(targetInfo, nextOffset, dnsServerName);
			nextOffset += dnsServerName.length;

			addBytes(targetInfo, nextOffset, timestampValue);
			nextOffset += timestampValue.length;

			addBytes(targetInfo, nextOffset, end);

			return targetInfo;
		}

		public byte[] createTargetInformationName(int type, String name, Charset charset)
		{
			byte[] nameBytes = name.getBytes(charset);
			byte[] targetInformationName = new byte[nameBytes.length + 4];

			addShort(targetInformationName, 0, type);
			addShort(targetInformationName, 2, nameBytes.length);
			addBytes(targetInformationName, 4, nameBytes);

			return targetInformationName;
		}

		public byte[] createTargetInformationTimestamp(Date timestamp)
		{
			byte[] targetInformationTimestamp = new byte[12];
			long filetime = (timestamp.getTime() + 11644473600000L) * 10000L;

			addShort(targetInformationTimestamp, 0, TARGET_INFO_TIMESTAMP);
			addShort(targetInformationTimestamp, 2, 8);
			addLong(targetInformationTimestamp, 4, filetime);

			return targetInformationTimestamp;
		}

		public byte[] createTargetInformationEnd()
		{
			byte[] targetInformationEnd = new byte[4];

			addShort(targetInformationEnd, 0, TARGET_INFO_END);
			addShort(targetInformationEnd, 2, 0);

			return targetInformationEnd;
		}
	}

	static class Version
	{
		private byte major;
		private byte minor;
		private short build;

		public Version(byte major, byte minor, short build)
		{
			this.major = major;
			this.minor = minor;
			this.build = build;
		}

		public byte[] createVersion()
		{
			byte[] version = new byte[8];

			addByte(version, 0, major);
			addByte(version, 1, minor);
			addShort(version, 2, build);
			addShort(version, 4, 0); // reserved
			addByte(version, 6, (byte)0); // reserved
			addByte(version, 7, (byte)15); // NTLM version

			return version;
		}
	}
}

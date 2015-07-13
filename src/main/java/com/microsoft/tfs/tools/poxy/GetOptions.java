/*
 * Poxy: a simple HTTP proxy for testing.
 * 
 * Copyright (c) Microsoft Corporation. All rights reserved.
 */

package com.microsoft.tfs.tools.poxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetOptions
{
    private final Map<String, List<String>> arguments = new HashMap<String, List<String>>();
    private final List<String> freeArguments = new ArrayList<String>();

    private final Option[] options;
    
    public GetOptions(Option[] options)
    {
        this.options = options;
    }
    
    public void parse(String[] args)
        throws OptionException
    {
        /* Create maps of short argument and long argument to option definition */
        Map<Character, Option> shortArgs = new HashMap<Character, Option>();
        Map<String, Option> longArgs = new HashMap<String, Option>();

        for (int i = 0; i < options.length; i++)
        {
            if (options[i].shortArg != 0)
                shortArgs.put(options[i].shortArg, options[i]);

            longArgs.put(options[i].longArg, options[i]);

            /* Setup default arguments */
            if (options[i].defaultArgument != null)
            {
                List<String> defaultArgumentList = new ArrayList<String>();
                defaultArgumentList.add(options[i].defaultArgument);
                
                arguments.put(options[i].longArg, defaultArgumentList);
            }
        }

        /* Parse the command-line */
        boolean doneWithOptions = false;
        Option lastOption = null;

        for (int i = 0; i < args.length; i++)
        {
            if (lastOption != null)
            {
                List<String> argumentList = arguments.get(lastOption.longArg);
                
                if(argumentList == null)
                {
                    argumentList = new ArrayList<String>();
                    arguments.put(lastOption.longArg, argumentList);                       
                }
                
                argumentList.add(args[i]);
                lastOption = null;
            }

            /* An argument */
            else if (doneWithOptions == false
                && ((args[i].startsWith("--") && args[i].length() > 2) || (args[i].startsWith("-") && args[i].length() > 1)))
            {
                Option thisOption = (args[i].startsWith("--")) ? longArgs.get(args[i].substring(2)) : shortArgs.get(args[i].charAt(1));

                if (thisOption == null)
                {
                    throw new OptionException("The option '" + args[i] + "' is unknown");
                }
                
                /* Short args can be combined: ie -ofoo is equivelant to -o foo */
                if(! args[i].startsWith("--") && args[i].length() > 2 && thisOption.argument)
                {
                    List<String> argumentList = arguments.get(thisOption.longArg);
                    
                    if(argumentList == null)
                    {
                        argumentList = new ArrayList<String>();
                        arguments.put(thisOption.longArg, argumentList);                       
                    }
                    
                    argumentList.add(args[i].substring(2));
                }
                
                /* Short arg is combined, ie '-v4' but no argument is accepted */
                else if(! args[i].startsWith("--") && args[i].length() > 2)
                {
                    throw new OptionException("The option '-" + thisOption.shortArg + "' does not take an argument");
                }
                
                /* Otherwise, next token is our argument */
                else if(thisOption.argument)
                {
                    lastOption = thisOption;
                }
                
                /* Otherwise, just mark it as seen */
                else
                {
                    List<String> argumentList = arguments.get(thisOption.longArg);
                    
                    if(argumentList == null)
                    {
                        argumentList = new ArrayList<String>();
                        arguments.put(thisOption.longArg, argumentList);                       
                    }
                    
                    argumentList.add("true");
                }
            }

            /* Single double-dash indicates to stop parsing options */
            else if (doneWithOptions == false && args[i].equals("--"))
            {
                doneWithOptions = true;
            }

            /* This is a free argument */
            else
            {
                freeArguments.add(args[i]);
            }
        }

        /* Forgot an argument */
        if (lastOption != null)
        {
            throw new OptionException("The option '" + lastOption + "' expects an argument");
        }
    }
    
    public String getArgument(String option)
    {
        List<String> argumentList = arguments.get(option);
        
        if(argumentList == null || argumentList.size() == 0)
        {
            return null;
        }
        
        return argumentList.get(argumentList.size() - 1);
    }
    
    public List<String> getArguments(String option)
    {
        return arguments.get(option);
    }
    
    public Map<String, List<String>> getArguments()
    {
        return arguments;
    }
    
    public List<String> getFreeArguments()
    {
        return freeArguments;
    }

    static class Option
    {
        protected final char shortArg;
        protected final String longArg;
        protected final boolean argument;
        protected final boolean allowMultiple;
        protected final String defaultArgument;

        public Option(String longArg)
        {
            this(longArg, (char) 0);
        }

        public Option(String longArg, char shortArg)
        {
            this(longArg, shortArg, false);
        }

        public Option(String longArg, char shortArg, boolean argument)
        {
            this(longArg, shortArg, argument, false, null);
        }
        
        public Option(String longArg, boolean argument)
        {
            this(longArg, (char) 0, argument, false, null);
        }
        
        public Option(String longArg, boolean argument, boolean allowMultiple)
        {
            this(longArg, (char) 0, argument, allowMultiple, null);
        }

        public Option(String longArg, boolean argument, String defaultArgument)
        {
            this(longArg, (char) 0, argument, false, defaultArgument);
        }
        
        public Option(String longArg, boolean argument, String defaultArgument, boolean allowMultiple)
        {
            this(longArg, (char) 0, argument, allowMultiple, defaultArgument);
        }

        public Option(String longArg, char shortArg, boolean argument, boolean allowMultiple)
        {
            this(longArg, shortArg, argument, allowMultiple, null);
        }
        
        public Option(String longArg, char shortArg, boolean argument, String defaultArgument)
        {
            this(longArg, shortArg, argument, false, defaultArgument);
        }
        
        public Option(String longArg, char shortArg, boolean argument, boolean allowMultiple, String defaultArgument)
        {
            assert (longArg != null);

            this.longArg = longArg;
            this.shortArg = shortArg;
            this.argument = argument;
            this.allowMultiple = allowMultiple;
            this.defaultArgument = defaultArgument;
        }
    }

    static class OptionException
        extends Exception
    {
        private static final long serialVersionUID = -3273413213652232971L;

        OptionException(String message)
        {
            super(message);
        }

        OptionException(String message, Throwable e)
        {
            super(message, e);
        }
    }
}

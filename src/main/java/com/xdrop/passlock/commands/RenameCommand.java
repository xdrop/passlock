package com.xdrop.passlock.commands;

import com.beust.jcommander.Parameter;
import com.xdrop.passlock.core.PasswordManager;
import com.xdrop.passlock.exceptions.CommandException;
import com.xdrop.passlock.exceptions.RefNotFoundException;

import java.util.List;

public class RenameCommand extends Command {

    public RenameCommand(PasswordManager passwordManager) {
        super(passwordManager);
    }

    @Parameter(names = {"--target", "-t"})
    private String targetName;

    @Parameter(description = "Reference to rename")
    private List<String> name;

    @Override
    public void execute() throws CommandException {

        if(name == null || name.size() < 1 || name.size() > 2){
            throw new CommandException("Invalid number of arguments.");
        }

        String ref;

        if (name.size() == 2) {
            ref = name.get(0);
            targetName = name.get(1);
        } else{
            ref = name.get(0);
        }


        tio.write("Please enter your master password: ");

        char[] masterPassword = tio.getSecure();

        try{

            if ( !passwordManager.exists(ref) ) {
                throw new CommandException("Invalid reference, password doesn't exist");
            }

            if (!passwordManager.unlocksMaster(masterPassword)) {
                throw new CommandException("Invalid master password");
            }

            if(targetName == null){
                tio.write("Enter the new name:");
                targetName = tio.getLine().trim();
            }

            passwordManager.rename(ref, targetName);
            tio.writeln("Password [" + ref + "] renamed to -> [" + targetName + "]");

        } catch (RefNotFoundException e) {
            e.printStackTrace();
        }


    }


}
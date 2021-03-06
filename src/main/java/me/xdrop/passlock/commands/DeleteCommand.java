package me.xdrop.passlock.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import me.xdrop.passlock.core.PasswordManager;
import me.xdrop.passlock.exceptions.CommandException;
import me.xdrop.passlock.exceptions.RefNotFoundException;
import me.xdrop.passlock.io.TextInputOutput;

import java.util.List;

@Parameters(commandDescription = "Deletes a password")
public class DeleteCommand extends Command {

    @Parameter(description = "Reference to delete")
    private List<String> name;

    public DeleteCommand(PasswordManager passwordManager) {
        super(passwordManager);
    }

    public DeleteCommand(PasswordManager passwordManager, TextInputOutput tio) {
        super(passwordManager, tio);
    }

    @Override
    public void execute() throws CommandException {

        if(name == null || name.size() != 1){
            throw new CommandException("Invalid number of arguments.");
        }

        String ref = name.get(0);

        char[] masterPassword = promptMasterPassword();


        try {

            if ( !passwordManager.exists(ref) ) {
                throw new CommandException("Invalid reference, password doesn't exist");
            }

            if (!passwordManager.unlocksMaster(masterPassword)) {
                throw new CommandException("Invalid master password");
            }

            tio.writeln("Are you absolutely sure you want to delete [" + ref + "]?");
            tio.write("Y/N:");

            String in = tio.getLine();

            if (in.equalsIgnoreCase("y")) {

                passwordManager.deletePassword(ref);
                tio.writeln("Deleted [" + ref + "]");

            } else {

                tio.writeln("Quitting");

            }

        } catch (RefNotFoundException e) {
            throw new CommandException("Invalid password");
        }

    }
}

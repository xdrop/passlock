package me.xdrop.passlock.commands;

import com.beust.jcommander.Parameter;
import me.xdrop.passlock.core.PasswordManager;
import me.xdrop.passlock.exceptions.CommandException;
import me.xdrop.passlock.io.TextInputOutput;

import java.util.List;

public class ListCommand extends Command {

    public ListCommand(PasswordManager passwordManager) {
        super(passwordManager);
    }

    public ListCommand(PasswordManager passwordManager, TextInputOutput tio) {
        super(passwordManager, tio);
    }

    @Override
    public void execute() throws CommandException {

        char[] masterPassword = promptMasterPassword();

        if(!passwordManager.unlocksMaster(masterPassword)) {
            throw new CommandException("Invalid master password");
        }

        tio.writeln("===========");
        tio.writeln(" Passwords ");
        tio.writeln("===========");
        tio.writeln("");

        List<String> passList = passwordManager.list();

        for (int i = 0; i < passList.size(); i++) {

            tio.writeln("[" + (i+1) + "] " + passList.get(i));

        }

    }

}

package me.xdrop.passlock.core;

import me.xdrop.passlock.exceptions.AlreadyExistsException;
import me.xdrop.passlock.exceptions.RefNotFoundException;

import me.xdrop.passlock.crypto.EncryptionModel;
import me.xdrop.passlock.crypto.aes.AESEncryptionData;
import me.xdrop.passlock.crypto.aes.AESEncryptionModel;
import me.xdrop.passlock.datasource.Datasource;
import me.xdrop.passlock.model.BufferedProcessor;
import me.xdrop.passlock.model.PasswordEntry;
import me.xdrop.passlock.search.FuzzySearcher;
import me.xdrop.passlock.utils.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;

public class PasswordManagerAES implements PasswordManager<AESEncryptionModel, AESEncryptionData> {

    private final static Logger LOG = LoggerFactory.getLogger(PasswordManagerAES.class);

    private EncryptionModel<AESEncryptionData> encryptionModel;

    private Datasource<AESEncryptionData> datasource;

    public PasswordManagerAES(Datasource<AESEncryptionData> datasource) {

        this.datasource = datasource;
        encryptionModel = new AESEncryptionModel();

    }

    /**
     * Stores and encrypts the given password, using an AES256 key
     * derived from the master password which is stored
     * in the datasource.
     *
     * @param description Description of what this password is for
     * @param newPassword The password to store in a *char* array
     * @param masterPass  The master password
     * @param reference   A unique reference identifier for this entry
     */
    @Override
    public void addPassword(String description, char[] newPassword, char[] masterPass, String reference)
            throws AlreadyExistsException {
        addPassword(description, ByteUtils.getBytes(newPassword), masterPass, reference);
    }

    /**
     * Stores and encrypts the given password, using an AES256 key
     * derived from the master password which is stored
     * in the datasource.
     *
     * @param description Description of what this password is for
     * @param newPassword The password to store in *bytes*
     * @param masterPass  The master password
     * @param reference   A unique reference identifier for this entry
     */
    @Override
    public void addPassword(String description, byte[] newPassword, char[] masterPass, String reference)
            throws AlreadyExistsException {

        LOG.debug("Encrypting entry [" + reference + "]...");

        AESEncryptionData encryptionData = encryptionModel.encrypt(newPassword, masterPass);

        PasswordEntry<AESEncryptionData> passwordEntry = new PasswordEntry<>();
        passwordEntry.setDescription(description);
        passwordEntry.setRef(reference);
        passwordEntry.setEncryptionData(encryptionData);

        LOG.debug("Storing in datasource...");

        datasource.addPass(reference, passwordEntry);

    }

    /**
     * Retrieves and decrypts the password requested, using an AES256
     * key which is retrieved from the datastore using the
     * master password.
     *
     * @param reference A unique reference identifier for this entry
     * @param masterKey The password with which this should be decrypted
     * @return The decrypted password in byte[] UTF-8 format
     * @throws RefNotFoundException Thrown if the reference used
     *                              could not be found in the
     *                              database.
     * @throws InvalidKeyException  Thrown if the password supplied
     *                              is incorrect and cannot
     *                              unlock the password.
     */
    @Override
    public byte[] getPassword(String reference, char[] masterKey)
            throws RefNotFoundException, InvalidKeyException {

        LOG.debug("Looking for " + reference + "...");
        PasswordEntry<AESEncryptionData> pass = datasource.getPass(reference);

        LOG.debug("Decrypting with key...");
        return encryptionModel.decrypt(pass.getEncryptionData(), masterKey);

    }

    /**
     * Renames a password reference to another
     *
     * @param reference    Old reference
     * @param newReference New reference
     * @throws RefNotFoundException Thrown if the old reference
     *                              doesn't exist
     */
    @Override
    public void rename(String reference, String newReference) throws RefNotFoundException {

        if(reference.equalsIgnoreCase("master")) throw new RefNotFoundException();

        LOG.debug("Renaming " + reference + " to " + newReference + "...");
        PasswordEntry<AESEncryptionData> passwordEntry = datasource.getPass(reference);

        passwordEntry.setRef(newReference);

        datasource.updatePass(reference, passwordEntry);

    }

    /**
     * Updates a password
     *
     * @param reference   Reference to the password
     * @param masterKey   The master key used to encrypt the new enty
     * @param newPassword The new password to store
     * @throws RefNotFoundException Thrown if the reference doesn't exist
     */
    @Override
    public void updatePassword(String reference, char[] masterKey, char[] newPassword) throws RefNotFoundException {

        if(reference.equalsIgnoreCase("master")) {

            LOG.debug("Updating master password");

            try {
                updateMasterPassword(masterKey, newPassword);
            } catch (InvalidKeyException e) {
                LOG.error("Master key was invalid", e);
            }

            return;
        }


        LOG.debug("Updating password for " + reference);

        AESEncryptionData encryptionData = encryptionModel.encrypt(ByteUtils.getBytes(newPassword), masterKey);

        PasswordEntry<AESEncryptionData> passwordEntry = new PasswordEntry<>();
        passwordEntry.setDescription("");
        passwordEntry.setRef(reference);
        passwordEntry.setEncryptionData(encryptionData);

        datasource.updatePass(reference, passwordEntry);

    }

    /**
     * Deletes a password
     *
     * @param reference Reference to the password
     * @throws RefNotFoundException Thrown if the reference doesn't exist
     */
    @Override
    public void deletePassword(String reference) throws RefNotFoundException {

        if(reference.equalsIgnoreCase("master")) throw new RefNotFoundException();

        datasource.delPass(reference);

    }

    public void updateMasterPassword(char[] oldMasterKey, char[] newMasterPassword) throws InvalidKeyException {

        LOG.info("Generating AES secret...");

        /* Store the master key */
        SecretKey secretKey = encryptionModel.generateSecret(newMasterPassword);

        try {
            LOG.debug("Deleting old master...");
            datasource.delPass("master");

            addPassword("The master key", secretKey.getEncoded(), newMasterPassword, "master");

            LOG.info("Re-encrypting entries...");
            updateMasterKey(oldMasterKey, ByteUtils.getChars(secretKey.getEncoded()));

        } catch (AlreadyExistsException | RefNotFoundException ignored) { /* can't happen */ }

        LOG.info("AES secret successfully stored!");

    }

    public void updateMasterKey(final char[] oldMasterKey, final char[] newMasterKey) throws InvalidKeyException {

        BufferedProcessor<PasswordEntry<AESEncryptionData>> updateTask;

        updateTask = new BufferedProcessor<PasswordEntry<AESEncryptionData>>() {

            List<? extends PasswordEntry<AESEncryptionData>> in;
            List<PasswordEntry<AESEncryptionData>> out;

            @Override
            public int getBufferSize() {
                return 500;
            }

            @Override
            public void receive(List<? extends PasswordEntry<AESEncryptionData>> in) {
                this.in = in;
                this.out = new ArrayList<>();
            }

            @Override
            public void process() throws Exception {

                for(PasswordEntry<AESEncryptionData> entry : in) {

                    if (entry.getRef().equalsIgnoreCase("master")) continue;
                    byte[] oldPayload;

                    try {
                        oldPayload = encryptionModel.decrypt(entry.getEncryptionData(), oldMasterKey);
                    } catch (InvalidKeyException e) {
                        /* not all passwords were encoded with the same key, so just skip.. */
                        continue;
                    }

                    AESEncryptionData newEncryptionData = encryptionModel.encrypt(oldPayload, newMasterKey);

                    PasswordEntry<AESEncryptionData> passwordEntry = entry.clone();
                    passwordEntry.setEncryptionData(newEncryptionData);

                    this.out.add(passwordEntry);

                }

            }

            @Override
            public List<PasswordEntry<AESEncryptionData>> send() {
                return this.out;
            }


        };

        try {
            datasource.bufferedUpdate(updateTask);
        } catch (InvalidKeyException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Unexpected exception during re-encryption", e);
        }

    }

    /**
     * Initializes and <b>resets</b> the database. All data in it will be lost!
     *
     * @param masterPass The master password to initialize the database
     *                   with.
     */
    @Override
    public void initializeDatasource(char[] masterPass) {

        LOG.info("Initializing datasource...");

        /* Initialize the datasource */
        datasource.reset();

        LOG.info("Generating AES secret...");

        /* Store the master key */
        SecretKey secretKey = encryptionModel.generateSecret(masterPass);

        try {
            addPassword("The master key", secretKey.getEncoded(), masterPass, "master");
        } catch (AlreadyExistsException ignored) { /* can't happen */ }

        LOG.info("AES secret succesfully stored!");

    }

    @Override
    public boolean isInitialized() {

        try {

            datasource.getPass("master");

            if (!datasource.isCreated()){
                return false;
            }
            return true;

        } catch (RefNotFoundException e) {
            return false;
        }

    }

    /**
     * Determines whether the password supplied can unlock the master key
     * (AES256)
     *
     * @param password The master password used to encrypt the master
     *                 key.
     * @return True  - if password is correct
     * False - otherwise
     */
    @Override
    public boolean unlocksMaster(char[] password) {

        try {

            getPassword("master", password);
            return true;

        } catch (RefNotFoundException e) {

            LOG.error("Master key not found", e);
            return false;

        } catch (InvalidKeyException e) {

            return false;

        }

    }

    /**
     * Returns the stored (and encrypted) master key (AES256)
     *
     * @param password The master password used to encrypt the master
     *                 key.
     * @return The master key in a char[] array
     * @throws InvalidKeyException If the password used is not correct
     */
    @Override
    public char[] getMasterKey(char[] password) throws InvalidKeyException {

        try {

            LOG.debug("Retrieving master key...");

            return ByteUtils.getChars(getPassword("master", password));

        } catch (RefNotFoundException e) {

            LOG.debug("Master key not found", e);
            return null;

        }

    }

    @Override
    public boolean exists(String ref) {

        try {
            datasource.getPass(ref);
        } catch (RefNotFoundException e) {
            return false;
        }

        return true;
    }

    /**
     * Searches the datasource to find a password entry matching
     * {@code ref}, and returns the list of the closest
     * matches.
     *
     * @param searcher The searching class to be used
     * @param query    The query string to search for
     * @param limit    The number of entries to return
     * @return The matched password references
     * @throws RefNotFoundException Thrown if the query failed to match any
     *                              entries with the specified cutoff level
     */
    @Override
    public List<String> search(FuzzySearcher searcher, String query, int limit) throws RefNotFoundException {

        List<String> passList = datasource.getPassList();
        passList.remove("master");

        return searcher.search(query, passList, limit);
    }

    /**
     * Lists all the passwords
     *
     * @return List of passwords
     */
    public List<String> list() {
        return datasource.getPassList();
    }

    /**
     * Copies a password
     *
     * @param from Source password
     * @param to   Target password
     * @throws RefNotFoundException   Thrown if the old reference
     *                                doesn't exist
     * @throws AlreadyExistsException Thrown if the new reference already
     *                                exists
     */
    @Override
    public void copy(String from, String to) throws RefNotFoundException, AlreadyExistsException {

        PasswordEntry<AESEncryptionData> old = datasource.getPass(from);
        old.setRef(to);

        datasource.addPass(to, old);

    }

    @Override
    public void setDatasource(Datasource<AESEncryptionData> datasource) {
        this.datasource = datasource;
    }

    public void setEncryptionModel(EncryptionModel<AESEncryptionData> encryptionModel) {
        this.encryptionModel = encryptionModel;
    }
}

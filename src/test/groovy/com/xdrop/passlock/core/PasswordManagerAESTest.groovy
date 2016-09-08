package com.xdrop.passlock.core

import com.xdrop.passlock.PassLock
import com.xdrop.passlock.utils.ByteUtils
import org.apache.log4j.PropertyConfigurator

import java.security.InvalidKeyException

class PasswordManagerAESTest extends GroovyTestCase {

    def pwman = new PasswordManagerAES();
    def masterPass = "mymaster";
    def encryptionPayload = "encryptme"

    void setUp() {

        super.setUp()

        PropertyConfigurator.configure(PassLock.loadPropertiesFile("log.properties"));

        pwman.initializeDatasource(masterPass.toCharArray())

        pwman.addPassword("Description", encryptionPayload.getBytes("UTF-8"), "nonaesmaster".toCharArray(), "def")

    }

    void testAddPassword() {

        pwman.addPassword("Description", "testpayload".toCharArray(), "nonaesmaster".toCharArray(), "test")

        assertNotNull pwman.getPassword("test", false, "nonaesmaster".toCharArray())
    }

    void testGetPassword() {

        def gotten = pwman.getPassword("def", false, "nonaesmaster".toCharArray())

        assertNotNull gotten

        assert gotten == encryptionPayload.getBytes("UTF-8")

        shouldFail(InvalidKeyException){
            pwman.getPassword("def", false, "wrongpassword23132@@\$£:\$%@\$^%:^@\$%&\$&:\$%^\$%\\^".toCharArray())
        }

    }

    void testInitializeDatasource() {

        def master = pwman.getPassword("master", false, "mymaster".getChars())

        assertNotNull master

    }

    void testUnlocksMaster() {

        assertTrue pwman.unlocksMaster("mymaster".getChars())
        assertFalse pwman.unlocksMaster("mymastern".getChars())

    }

    void testGetMasterKey() {

        def masterKey = pwman.getMasterKey(masterPass.toCharArray())

        pwman.addPassword("New pass", "hideme".getChars(), masterKey, "newpass")

        assertNotNull masterKey

        assert pwman.getPassword("newpass", false, masterKey) == "hideme".getBytes("UTF-8")

    }
}
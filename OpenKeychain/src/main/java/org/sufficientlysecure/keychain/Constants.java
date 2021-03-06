/*
 * Copyright (C) 2010-2014 Thialfihar <thi@thialfihar.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain;

import android.os.Environment;

import org.spongycastle.bcpg.CompressionAlgorithmTags;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.sufficientlysecure.keychain.remote.ui.AppsListActivity;
import org.sufficientlysecure.keychain.ui.DecryptActivity;
import org.sufficientlysecure.keychain.ui.EncryptActivity;
import org.sufficientlysecure.keychain.ui.KeyListActivity;

public final class Constants {

    public static final boolean DEBUG = BuildConfig.DEBUG;

    public static final String TAG = "Keychain";

    public static final String PACKAGE_NAME = "org.sufficientlysecure.keychain";

    // as defined in http://tools.ietf.org/html/rfc3156, section 7
    public static final String NFC_MIME = "application/pgp-keys";

    // used by QR Codes (Guardian Project, Monkeysphere compatiblity)
    public static final String FINGERPRINT_SCHEME = "openpgp4fpr";

    // Not BC due to the use of Spongy Castle for Android
    public static final String SC = BouncyCastleProvider.PROVIDER_NAME;
    public static final String BOUNCY_CASTLE_PROVIDER_NAME = SC;

    public static final String INTENT_PREFIX = PACKAGE_NAME + ".action.";

    public static final class Path {
        public static final String APP_DIR = Environment.getExternalStorageDirectory()
                + "/OpenKeychain";
        public static final String APP_DIR_FILE = APP_DIR + "/export.asc";
    }

    public static final class Pref {
        public static final String DEFAULT_ENCRYPTION_ALGORITHM = "defaultEncryptionAlgorithm";
        public static final String DEFAULT_HASH_ALGORITHM = "defaultHashAlgorithm";
        public static final String DEFAULT_ASCII_ARMOR = "defaultAsciiArmor";
        public static final String DEFAULT_MESSAGE_COMPRESSION = "defaultMessageCompression";
        public static final String DEFAULT_FILE_COMPRESSION = "defaultFileCompression";
        public static final String PASSPHRASE_CACHE_TTL = "passphraseCacheTtl";
        public static final String LANGUAGE = "language";
        public static final String FORCE_V3_SIGNATURES = "forceV3Signatures";
        public static final String KEY_SERVERS = "keyServers";
    }

    public static final class Defaults {
        public static final String KEY_SERVERS = "pool.sks-keyservers.net, subkeys.pgp.net, pgp.mit.edu";
    }

    public static final class DrawerItems {
        public static final Class KEY_LIST = KeyListActivity.class;
        public static final Class ENCRYPT = EncryptActivity.class;
        public static final Class DECRYPT = DecryptActivity.class;
        public static final Class REGISTERED_APPS_LIST = AppsListActivity.class;
        public static final Class[] ARRAY = new Class[]{
                KEY_LIST,
                ENCRYPT,
                DECRYPT,
                REGISTERED_APPS_LIST
        };
    }

    public static final class choice {
        public static final class algorithm {
            // TODO: legacy reasons :/ better: PublicKeyAlgorithmTags
            public static final int dsa = 0x21070001;
            public static final int elgamal = 0x21070002;
            public static final int rsa = 0x21070003;
        }

        public static final class compression {
            // TODO: legacy reasons :/ better: CompressionAlgorithmTags.UNCOMPRESSED
            public static final int none = 0x21070001;
            public static final int zlib = CompressionAlgorithmTags.ZLIB;
            public static final int bzip2 = CompressionAlgorithmTags.BZIP2;
            public static final int zip = CompressionAlgorithmTags.ZIP;
        }
    }

    public static final class key {
        public static final int none = 0;
        public static final int symmetric = -1;
    }
}

/*
 * Copyright (C) 2010 Thialfihar <thi@thialfihar.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thialfihar.android.apg;

import org.bouncycastle2.bcpg.HashAlgorithmTags;
import org.bouncycastle2.openpgp.PGPEncryptedData;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity {
    private IntegerListPreference mPassPhraseCacheTtl = null;
    private IntegerListPreference mEncryptionAlgorithm = null;
    private IntegerListPreference mHashAlgorithm = null;
    private IntegerListPreference mMessageCompression = null;
    private IntegerListPreference mFileCompression = null;
    private CheckBoxPreference mAsciiArmour = null;
    private Preferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPreferences = Preferences.getPreferences(this);

        addPreferencesFromResource(R.xml.apg_preferences);

        mPassPhraseCacheTtl = (IntegerListPreference) findPreference(Constants.pref.pass_phrase_cache_ttl);
        mPassPhraseCacheTtl.setValue("" + mPreferences.getPassPhraseCacheTtl());
        mPassPhraseCacheTtl.setSummary(mPassPhraseCacheTtl.getEntry());
        mPassPhraseCacheTtl.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                mPassPhraseCacheTtl.setValue(newValue.toString());
                mPassPhraseCacheTtl.setSummary(mPassPhraseCacheTtl.getEntry());
                mPreferences.setPassPhraseCacheTtl(Integer.parseInt(newValue.toString()));
                BaseActivity.startCacheService(PreferencesActivity.this, mPreferences);
                return false;
            }
        });

        mEncryptionAlgorithm = (IntegerListPreference) findPreference(Constants.pref.default_encryption_algorithm);
        int valueIds[] = {
                PGPEncryptedData.AES_128, PGPEncryptedData.AES_192, PGPEncryptedData.AES_256,
                PGPEncryptedData.BLOWFISH, PGPEncryptedData.TWOFISH, PGPEncryptedData.CAST5,
                PGPEncryptedData.DES, PGPEncryptedData.TRIPLE_DES, PGPEncryptedData.IDEA,
        };
        String entries[] = {
                "AES-128", "AES-192", "AES-256",
                "Blowfish", "Twofish", "CAST5",
                "DES", "Triple DES", "IDEA",
        };
        String values[] = new String[valueIds.length];
        for (int i = 0; i < values.length; ++i) {
            values[i] = "" + valueIds[i];
        }
        mEncryptionAlgorithm.setEntries(entries);
        mEncryptionAlgorithm.setEntryValues(values);
        mEncryptionAlgorithm.setValue("" + mPreferences.getDefaultEncryptionAlgorithm());
        mEncryptionAlgorithm.setSummary(mEncryptionAlgorithm.getEntry());
        mEncryptionAlgorithm.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                mEncryptionAlgorithm.setValue(newValue.toString());
                mEncryptionAlgorithm.setSummary(mEncryptionAlgorithm.getEntry());
                mPreferences.setDefaultEncryptionAlgorithm(Integer.parseInt(newValue.toString()));
                return false;
            }
        });

        mHashAlgorithm = (IntegerListPreference) findPreference(Constants.pref.default_hash_algorithm);
        valueIds = new int[] {
                HashAlgorithmTags.MD5, HashAlgorithmTags.RIPEMD160, HashAlgorithmTags.SHA1,
                HashAlgorithmTags.SHA224, HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA384,
                HashAlgorithmTags.SHA512,
        };
        entries = new String[] {
                "MD5", "RIPEMD-160", "SHA-1",
                "SHA-224", "SHA-256", "SHA-384",
                "SHA-512",
        };
        values = new String[valueIds.length];
        for (int i = 0; i < values.length; ++i) {
            values[i] = "" + valueIds[i];
        }
        mHashAlgorithm.setEntries(entries);
        mHashAlgorithm.setEntryValues(values);
        mHashAlgorithm.setValue("" + mPreferences.getDefaultHashAlgorithm());
        mHashAlgorithm.setSummary(mHashAlgorithm.getEntry());
        mHashAlgorithm.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                mHashAlgorithm.setValue(newValue.toString());
                mHashAlgorithm.setSummary(mHashAlgorithm.getEntry());
                mPreferences.setDefaultHashAlgorithm(Integer.parseInt(newValue.toString()));
                return false;
            }
        });

        mMessageCompression = (IntegerListPreference) findPreference(Constants.pref.default_message_compression);
        valueIds = new int[] {
                Id.choice.compression.none, Id.choice.compression.zip,
                Id.choice.compression.bzip2, Id.choice.compression.zlib,
        };
        entries = new String[] {
                getString(R.string.choice_none), "ZIP",
                "BZIP2", "ZLIB",
        };
        values = new String[valueIds.length];
        for (int i = 0; i < values.length; ++i) {
            values[i] = "" + valueIds[i];
        }
        mMessageCompression.setEntries(entries);
        mMessageCompression.setEntryValues(values);
        mMessageCompression.setValue("" + mPreferences.getDefaultMessageCompression());
        mMessageCompression.setSummary(mMessageCompression.getEntry());
        mMessageCompression.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                mMessageCompression.setValue(newValue.toString());
                mMessageCompression.setSummary(mMessageCompression.getEntry());
                mPreferences.setDefaultMessageCompression(Integer.parseInt(newValue.toString()));
                return false;
            }
        });

        mFileCompression = (IntegerListPreference) findPreference(Constants.pref.default_file_compression);
        mFileCompression.setEntries(entries);
        mFileCompression.setEntryValues(values);
        mFileCompression.setValue("" + mPreferences.getDefaultFileCompression());
        mFileCompression.setSummary(mFileCompression.getEntry());
        mFileCompression.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                mFileCompression.setValue(newValue.toString());
                mFileCompression.setSummary(mFileCompression.getEntry());
                mPreferences.setDefaultFileCompression(Integer.parseInt(newValue.toString()));
                return false;
            }
        });

        mAsciiArmour = (CheckBoxPreference) findPreference(Constants.pref.default_ascii_armour);
        mAsciiArmour.setChecked(mPreferences.getDefaultAsciiArmour());
        mAsciiArmour.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                mAsciiArmour.setChecked((Boolean)newValue);
                mPreferences.setDefaultAsciiArmour((Boolean)newValue);
                return false;
            }
        });
    }
}

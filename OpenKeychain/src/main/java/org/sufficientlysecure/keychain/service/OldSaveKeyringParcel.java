/*
 * Copyright (C) 2014 Ash Hughes <ashes-iontach@hotmail.com>
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

package org.sufficientlysecure.keychain.service;

import android.os.Parcel;
import android.os.Parcelable;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.pgp.PgpConversionHelper;
import org.sufficientlysecure.keychain.pgp.UncachedSecretKey;
import org.sufficientlysecure.keychain.util.IterableIterator;
import org.sufficientlysecure.keychain.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

/** Class for parcelling data between ui and services.
 * This class is outdated and scheduled for removal, pending a rewrite of the
 * EditKeyActivity and save keyring routines.
 */
@Deprecated
public class OldSaveKeyringParcel implements Parcelable {

    public ArrayList<String> userIds;
    public ArrayList<String> originalIDs;
    public ArrayList<String> deletedIDs;
    public boolean[] newIDs;
    public boolean primaryIDChanged;
    public boolean[] moddedKeys;
    public ArrayList<UncachedSecretKey> deletedKeys;
    public ArrayList<Calendar> keysExpiryDates;
    public ArrayList<Integer> keysUsages;
    public String newPassphrase;
    public String oldPassphrase;
    public boolean[] newKeys;
    public ArrayList<UncachedSecretKey> keys;
    public String originalPrimaryID;

    public OldSaveKeyringParcel() {}

    private OldSaveKeyringParcel(Parcel source) {
        userIds = (ArrayList<String>) source.readSerializable();
        originalIDs = (ArrayList<String>) source.readSerializable();
        deletedIDs = (ArrayList<String>) source.readSerializable();
        newIDs = source.createBooleanArray();
        primaryIDChanged = source.readByte() != 0;
        moddedKeys = source.createBooleanArray();
        byte[] tmp = source.createByteArray();
        if (tmp == null) {
            deletedKeys = null;
        } else {
            deletedKeys = PgpConversionHelper.BytesToPGPSecretKeyList(tmp);
        }
        keysExpiryDates = (ArrayList<Calendar>) source.readSerializable();
        keysUsages = source.readArrayList(Integer.class.getClassLoader());
        newPassphrase = source.readString();
        oldPassphrase = source.readString();
        newKeys = source.createBooleanArray();
        keys = PgpConversionHelper.BytesToPGPSecretKeyList(source.createByteArray());
        originalPrimaryID = source.readString();
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeSerializable(userIds); //might not be the best method to store.
        destination.writeSerializable(originalIDs);
        destination.writeSerializable(deletedIDs);
        destination.writeBooleanArray(newIDs);
        destination.writeByte((byte) (primaryIDChanged ? 1 : 0));
        destination.writeBooleanArray(moddedKeys);
        destination.writeByteArray(encodeArrayList(deletedKeys));
        destination.writeSerializable(keysExpiryDates);
        destination.writeList(keysUsages);
        destination.writeString(newPassphrase);
        destination.writeString(oldPassphrase);
        destination.writeBooleanArray(newKeys);
        destination.writeByteArray(encodeArrayList(keys));
        destination.writeString(originalPrimaryID);
    }

    public static final Creator<OldSaveKeyringParcel> CREATOR = new Creator<OldSaveKeyringParcel>() {
        public OldSaveKeyringParcel createFromParcel(final Parcel source) {
            return new OldSaveKeyringParcel(source);
        }

        public OldSaveKeyringParcel[] newArray(final int size) {
            return new OldSaveKeyringParcel[size];
        }
    };

    private static byte[] encodeArrayList(ArrayList<UncachedSecretKey> list) {
        if(list.isEmpty()) {
            return null;
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        for(UncachedSecretKey key : new IterableIterator<UncachedSecretKey>(list.iterator())) {
            try {
                key.encodeSecretKey(os);
            } catch (IOException e) {
                Log.e(Constants.TAG, "Error while converting ArrayList<UncachedSecretKey> to byte[]!", e);
            }
        }
        return os.toByteArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }
}

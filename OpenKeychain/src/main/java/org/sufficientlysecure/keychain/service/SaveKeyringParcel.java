package org.sufficientlysecure.keychain.service;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.HashMap;

/** This class is a a transferable representation for a collection of changes
 * to be done on a keyring.
 *
 * This class should include all types of operations supported in the backend.
 *
 * All changes are done in a differential manner. Besides the two key
 * identification attributes, all attributes may be null, which indicates no
 * change to the keyring. This is also the reason why boxed values are used
 * instead of primitives in the subclasses.
 *
 * Application of operations in the backend should be fail-fast, which means an
 * error in any included operation (for example revocation of a non-existent
 * subkey) will cause the operation as a whole to fail.
 */
public class SaveKeyringParcel implements Parcelable {

    // the master key id to be edited
    private final long mMasterKeyId;
    // the key fingerprint, for safety
    private final byte[] mFingerprint;

    public String newPassphrase;

    public String[] addUserIds;
    public SubkeyAdd[] addSubKeys;

    public HashMap<Long, SubkeyChange> changeSubKeys;
    public String changePrimaryUserId;

    public String[] revokeUserIds;
    public long[] revokeSubKeys;

    public SaveKeyringParcel(long masterKeyId, byte[] fingerprint) {
        mMasterKeyId = masterKeyId;
        mFingerprint = fingerprint;
    }

    // performance gain for using Parcelable here would probably be negligible,
    // use Serializable instead.
    public static class SubkeyAdd implements Serializable {
        public final int mAlgorithm;
        public final int mKeysize;
        public final int mFlags;
        public final Long mExpiry;
        public SubkeyAdd(int algorithm, int keysize, int flags, Long expiry) {
            mAlgorithm = algorithm;
            mKeysize = keysize;
            mFlags = flags;
            mExpiry = expiry;
        }
    }

    public static class SubkeyChange implements Serializable {
        public final long mKeyId;
        public final Integer mFlags;
        public final Long mExpiry;
        public SubkeyChange(long keyId, Integer flags, Long expiry) {
            mKeyId = keyId;
            mFlags = flags;
            mExpiry = expiry;
        }
    }

    public SaveKeyringParcel(Parcel source) {
        mMasterKeyId = source.readLong();
        mFingerprint = source.createByteArray();

        addUserIds = source.createStringArray();
        addSubKeys = (SubkeyAdd[]) source.readSerializable();

        changeSubKeys = (HashMap<Long,SubkeyChange>) source.readSerializable();
        changePrimaryUserId = source.readString();

        revokeUserIds = source.createStringArray();
        revokeSubKeys = source.createLongArray();
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeLong(mMasterKeyId);
        destination.writeByteArray(mFingerprint);

        destination.writeStringArray(addUserIds);
        destination.writeSerializable(addSubKeys);

        destination.writeSerializable(changeSubKeys);
        destination.writeString(changePrimaryUserId);

        destination.writeStringArray(revokeUserIds);
        destination.writeLongArray(revokeSubKeys);
    }

    public static final Creator<SaveKeyringParcel> CREATOR = new Creator<SaveKeyringParcel>() {
        public SaveKeyringParcel createFromParcel(final Parcel source) {
            return new SaveKeyringParcel(source);
        }

        public SaveKeyringParcel[] newArray(final int size) {
            return new SaveKeyringParcel[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

}

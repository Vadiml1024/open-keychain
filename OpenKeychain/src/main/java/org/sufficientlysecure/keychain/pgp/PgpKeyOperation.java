/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.pgp;

import org.spongycastle.bcpg.CompressionAlgorithmTags;
import org.spongycastle.bcpg.HashAlgorithmTags;
import org.spongycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.spongycastle.bcpg.sig.KeyFlags;
import org.spongycastle.jce.spec.ElGamalParameterSpec;
import org.spongycastle.openpgp.PGPEncryptedData;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPKeyPair;
import org.spongycastle.openpgp.PGPKeyRingGenerator;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.spongycastle.openpgp.PGPSecretKey;
import org.spongycastle.openpgp.PGPSecretKeyRing;
import org.spongycastle.openpgp.PGPSignature;
import org.spongycastle.openpgp.PGPSignatureGenerator;
import org.spongycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.spongycastle.openpgp.PGPSignatureSubpacketVector;
import org.spongycastle.openpgp.PGPUtil;
import org.spongycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.spongycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.spongycastle.openpgp.operator.PGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.PGPDigestCalculator;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.pgp.exception.PgpGeneralMsgIdException;
import org.sufficientlysecure.keychain.service.OldSaveKeyringParcel;
import org.sufficientlysecure.keychain.service.SaveKeyringParcel;
import org.sufficientlysecure.keychain.util.IterableIterator;
import org.sufficientlysecure.keychain.util.Primes;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

/**
 * This class is the single place where ALL operations that actually modify a PGP public or secret
 * key take place.
 * <p/>
 * Note that no android specific stuff should be done here, ie no imports from com.android.
 * <p/>
 * All operations support progress reporting to a Progressable passed on initialization.
 * This indicator may be null.
 */
public class PgpKeyOperation {
    private Progressable mProgress;

    private static final int[] PREFERRED_SYMMETRIC_ALGORITHMS = new int[]{
            SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_192,
            SymmetricKeyAlgorithmTags.AES_128, SymmetricKeyAlgorithmTags.CAST5,
            SymmetricKeyAlgorithmTags.TRIPLE_DES};
    private static final int[] PREFERRED_HASH_ALGORITHMS = new int[]{HashAlgorithmTags.SHA1,
            HashAlgorithmTags.SHA256, HashAlgorithmTags.RIPEMD160};
    private static final int[] PREFERRED_COMPRESSION_ALGORITHMS = new int[]{
            CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.BZIP2,
            CompressionAlgorithmTags.ZIP};

    public PgpKeyOperation(Progressable progress) {
        super();
        this.mProgress = progress;
    }

    void updateProgress(int message, int current, int total) {
        if (mProgress != null) {
            mProgress.setProgress(message, current, total);
        }
    }

    void updateProgress(int current, int total) {
        if (mProgress != null) {
            mProgress.setProgress(current, total);
        }
    }

    /**
     * Creates new secret key.
     *
     * @param algorithmChoice
     * @param keySize
     * @param passphrase
     * @param isMasterKey
     * @return A newly created PGPSecretKey
     * @throws NoSuchAlgorithmException
     * @throws PGPException
     * @throws NoSuchProviderException
     * @throws PgpGeneralMsgIdException
     * @throws InvalidAlgorithmParameterException
     */

    // TODO: key flags?
    public PGPSecretKey createKey(int algorithmChoice, int keySize, String passphrase,
                                  boolean isMasterKey)
            throws NoSuchAlgorithmException, PGPException, NoSuchProviderException,
            PgpGeneralMsgIdException, InvalidAlgorithmParameterException {

        if (keySize < 512) {
            throw new PgpGeneralMsgIdException(R.string.error_key_size_minimum512bit);
        }

        if (passphrase == null) {
            passphrase = "";
        }

        int algorithm;
        KeyPairGenerator keyGen;

        switch (algorithmChoice) {
            case Constants.choice.algorithm.dsa: {
                keyGen = KeyPairGenerator.getInstance("DSA", Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                keyGen.initialize(keySize, new SecureRandom());
                algorithm = PGPPublicKey.DSA;
                break;
            }

            case Constants.choice.algorithm.elgamal: {
                if (isMasterKey) {
                    throw new PgpGeneralMsgIdException(R.string.error_master_key_must_not_be_el_gamal);
                }
                keyGen = KeyPairGenerator.getInstance("ElGamal", Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                BigInteger p = Primes.getBestPrime(keySize);
                BigInteger g = new BigInteger("2");

                ElGamalParameterSpec elParams = new ElGamalParameterSpec(p, g);

                keyGen.initialize(elParams);
                algorithm = PGPPublicKey.ELGAMAL_ENCRYPT;
                break;
            }

            case Constants.choice.algorithm.rsa: {
                keyGen = KeyPairGenerator.getInstance("RSA", Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                keyGen.initialize(keySize, new SecureRandom());

                algorithm = PGPPublicKey.RSA_GENERAL;
                break;
            }

            default: {
                throw new PgpGeneralMsgIdException(R.string.error_unknown_algorithm_choice);
            }
        }

        // build new key pair
        PGPKeyPair keyPair = new JcaPGPKeyPair(algorithm, keyGen.generateKeyPair(), new Date());

        // define hashing and signing algos
        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(
                HashAlgorithmTags.SHA1);

        // Build key encrypter and decrypter based on passphrase
        PBESecretKeyEncryptor keyEncryptor = new JcePBESecretKeyEncryptorBuilder(
                PGPEncryptedData.CAST5, sha1Calc)
                .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(passphrase.toCharArray());

        try {
            return new PGPSecretKey(keyPair.getPrivateKey(), keyPair.getPublicKey(),
                    sha1Calc, isMasterKey, keyEncryptor).getEncoded();
        } catch(IOException e) {
            throw new PgpGeneralMsgIdException(R.string.error_encoding);
        }
    }

    public Pair<UncachedKeyRing,UncachedKeyRing> buildNewSecretKey(
        OldSaveKeyringParcel saveParcel)
            throws PgpGeneralMsgIdException, PGPException, SignatureException, IOException {

        int usageId = saveParcel.keysUsages.get(0);
        boolean canSign;
        String mainUserId = saveParcel.userIds.get(0);

        PGPSecretKey masterKey = saveParcel.keys.get(0).getSecretKeyExternal();

        // this removes all userIds and certifications previously attached to the masterPublicKey
        PGPPublicKey masterPublicKey = masterKey.getPublicKey();

        PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(saveParcel.oldPassphrase.toCharArray());
        PGPPrivateKey masterPrivateKey = masterKey.extractPrivateKey(keyDecryptor);

        updateProgress(R.string.progress_certifying_master_key, 20, 100);

        for (String userId : saveParcel.userIds) {
            PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                    masterPublicKey.getAlgorithm(), HashAlgorithmTags.SHA1)
                    .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
            PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);

            sGen.init(PGPSignature.POSITIVE_CERTIFICATION, masterPrivateKey);

            PGPSignature certification = sGen.generateCertification(userId, masterPublicKey);
            masterPublicKey = PGPPublicKey.addCertification(masterPublicKey, userId, certification);
        }

        PGPKeyPair masterKeyPair = new PGPKeyPair(masterPublicKey, masterPrivateKey);

        PGPSignatureSubpacketGenerator hashedPacketsGen = new PGPSignatureSubpacketGenerator();
        PGPSignatureSubpacketGenerator unhashedPacketsGen = new PGPSignatureSubpacketGenerator();

        hashedPacketsGen.setKeyFlags(true, usageId);

        hashedPacketsGen.setPreferredSymmetricAlgorithms(true, PREFERRED_SYMMETRIC_ALGORITHMS);
        hashedPacketsGen.setPreferredHashAlgorithms(true, PREFERRED_HASH_ALGORITHMS);
        hashedPacketsGen.setPreferredCompressionAlgorithms(true, PREFERRED_COMPRESSION_ALGORITHMS);

        if (saveParcel.keysExpiryDates.get(0) != null) {
            Calendar creationDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            creationDate.setTime(masterPublicKey.getCreationTime());
            Calendar expiryDate = saveParcel.keysExpiryDates.get(0);
            //note that the below, (a/c) - (b/c) is *not* the same as (a - b) /c
            //here we purposefully ignore partial days in each date - long type has no fractional part!
            long numDays = (expiryDate.getTimeInMillis() / 86400000) -
                    (creationDate.getTimeInMillis() / 86400000);
            if (numDays <= 0) {
                throw new PgpGeneralMsgIdException(R.string.error_expiry_must_come_after_creation);
            }
            hashedPacketsGen.setKeyExpirationTime(false, numDays * 86400);
        } else {
            hashedPacketsGen.setKeyExpirationTime(false, 0);
            // do this explicitly, although since we're rebuilding,
            // this happens anyway
        }

        updateProgress(R.string.progress_building_master_key, 30, 100);

        // define hashing and signing algos
        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(
                HashAlgorithmTags.SHA1);
        PGPContentSignerBuilder certificationSignerBuilder = new JcaPGPContentSignerBuilder(
                masterKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1);

        // Build key encrypter based on passphrase
        PBESecretKeyEncryptor keyEncryptor = new JcePBESecretKeyEncryptorBuilder(
                PGPEncryptedData.CAST5, sha1Calc)
                .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                        saveParcel.newPassphrase.toCharArray());

        PGPKeyRingGenerator keyGen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION,
                masterKeyPair, mainUserId, sha1Calc, hashedPacketsGen.generate(),
                unhashedPacketsGen.generate(), certificationSignerBuilder, keyEncryptor);

        updateProgress(R.string.progress_adding_sub_keys, 40, 100);

        for (int i = 1; i < saveParcel.keys.size(); ++i) {
            updateProgress(40 + 40 * (i - 1) / (saveParcel.keys.size() - 1), 100);

            PGPSecretKey subKey = saveParcel.keys.get(i).getSecretKeyExternal();
            PGPPublicKey subPublicKey = subKey.getPublicKey();

            PBESecretKeyDecryptor keyDecryptor2 = new JcePBESecretKeyDecryptorBuilder()
                    .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                            saveParcel.oldPassphrase.toCharArray());
            PGPPrivateKey subPrivateKey = subKey.extractPrivateKey(keyDecryptor2);

            // TODO: now used without algorithm and creation time?! (APG 1)
            PGPKeyPair subKeyPair = new PGPKeyPair(subPublicKey, subPrivateKey);

            hashedPacketsGen = new PGPSignatureSubpacketGenerator();
            unhashedPacketsGen = new PGPSignatureSubpacketGenerator();

            usageId = saveParcel.keysUsages.get(i);
            canSign = (usageId & KeyFlags.SIGN_DATA) > 0; //todo - separate function for this
            if (canSign) {
                Date todayDate = new Date(); //both sig times the same
                // cross-certify signing keys
                hashedPacketsGen.setSignatureCreationTime(false, todayDate); //set outer creation time
                PGPSignatureSubpacketGenerator subHashedPacketsGen = new PGPSignatureSubpacketGenerator();
                subHashedPacketsGen.setSignatureCreationTime(false, todayDate); //set inner creation time
                PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                        subPublicKey.getAlgorithm(), PGPUtil.SHA1)
                        .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);
                sGen.init(PGPSignature.PRIMARYKEY_BINDING, subPrivateKey);
                sGen.setHashedSubpackets(subHashedPacketsGen.generate());
                PGPSignature certification = sGen.generateCertification(masterPublicKey,
                        subPublicKey);
                unhashedPacketsGen.setEmbeddedSignature(false, certification);
            }
            hashedPacketsGen.setKeyFlags(false, usageId);

            if (saveParcel.keysExpiryDates.get(i) != null) {
                Calendar creationDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                creationDate.setTime(subPublicKey.getCreationTime());
                Calendar expiryDate = saveParcel.keysExpiryDates.get(i);
                //note that the below, (a/c) - (b/c) is *not* the same as (a - b) /c
                //here we purposefully ignore partial days in each date - long type has no fractional part!
                long numDays = (expiryDate.getTimeInMillis() / 86400000) -
                        (creationDate.getTimeInMillis() / 86400000);
                if (numDays <= 0) {
                    throw new PgpGeneralMsgIdException(R.string.error_expiry_must_come_after_creation);
                }
                hashedPacketsGen.setKeyExpirationTime(false, numDays * 86400);
            } else {
                hashedPacketsGen.setKeyExpirationTime(false, 0);
                // do this explicitly, although since we're rebuilding,
                // this happens anyway
            }

            keyGen.addSubKey(subKeyPair, hashedPacketsGen.generate(), unhashedPacketsGen.generate());
        }

        PGPSecretKeyRing secretKeyRing = keyGen.generateSecretKeyRing();
        PGPPublicKeyRing publicKeyRing = keyGen.generatePublicKeyRing();

        return new Pair(new UncachedKeyRing(secretKeyRing), new UncachedKeyRing(publicKeyRing));

    }

    public Pair<UncachedKeyRing, UncachedKeyRing> buildSecretKey(WrappedSecretKeyRing wmKR,
                                                                 WrappedPublicKeyRing wpKR,
                                                                 OldSaveKeyringParcel saveParcel)
            throws PgpGeneralMsgIdException, PGPException, SignatureException, IOException {

        PGPSecretKeyRing mKR = wmKR.getRing();
        PGPPublicKeyRing pKR = wpKR.getRing();

        updateProgress(R.string.progress_building_key, 0, 100);

        if (saveParcel.oldPassphrase == null) {
            saveParcel.oldPassphrase = "";
        }
        if (saveParcel.newPassphrase == null) {
            saveParcel.newPassphrase = "";
        }

        /*
        IDs - NB This might not need to happen later, if we change the way the primary ID is chosen
            remove deleted ids
            if the primary ID changed we need to:
                remove all of the IDs from the keyring, saving their certifications
                add them all in again, updating certs of IDs which have changed
            else
                remove changed IDs and add in with new certs

            if the master key changed, we need to remove the primary ID certification, so we can add
            the new one when it is generated, and they don't conflict

        Keys
            remove deleted keys
            if a key is modified, re-sign it
                do we need to remove and add in?

        Todo
            identify more things which need to be preserved - e.g. trust levels?
                    user attributes
        */

        if (saveParcel.deletedKeys != null) {
            for (UncachedSecretKey dKey : saveParcel.deletedKeys) {
                mKR = PGPSecretKeyRing.removeSecretKey(mKR, dKey.getSecretKeyExternal());
            }
        }

        PGPSecretKey masterKey = mKR.getSecretKey();
        PGPPublicKey masterPublicKey = masterKey.getPublicKey();

        int usageId = saveParcel.keysUsages.get(0);
        boolean canSign;
        String mainUserId = saveParcel.userIds.get(0);

        PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(saveParcel.oldPassphrase.toCharArray());
        PGPPrivateKey masterPrivateKey = masterKey.extractPrivateKey(keyDecryptor);

        updateProgress(R.string.progress_certifying_master_key, 20, 100);

        boolean anyIDChanged = false;
        for (String delID : saveParcel.deletedIDs) {
            anyIDChanged = true;
            masterPublicKey = PGPPublicKey.removeCertification(masterPublicKey, delID);
        }

        int userIDIndex = 0;

        PGPSignatureSubpacketGenerator hashedPacketsGen = new PGPSignatureSubpacketGenerator();
        PGPSignatureSubpacketGenerator unhashedPacketsGen = new PGPSignatureSubpacketGenerator();

        hashedPacketsGen.setKeyFlags(true, usageId);

        hashedPacketsGen.setPreferredSymmetricAlgorithms(true, PREFERRED_SYMMETRIC_ALGORITHMS);
        hashedPacketsGen.setPreferredHashAlgorithms(true, PREFERRED_HASH_ALGORITHMS);
        hashedPacketsGen.setPreferredCompressionAlgorithms(true, PREFERRED_COMPRESSION_ALGORITHMS);

        if (saveParcel.keysExpiryDates.get(0) != null) {
            Calendar creationDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            creationDate.setTime(masterPublicKey.getCreationTime());
            Calendar expiryDate = saveParcel.keysExpiryDates.get(0);
            //note that the below, (a/c) - (b/c) is *not* the same as (a - b) /c
            //here we purposefully ignore partial days in each date - long type has no fractional part!
            long numDays = (expiryDate.getTimeInMillis() / 86400000) -
                    (creationDate.getTimeInMillis() / 86400000);
            if (numDays <= 0) {
                throw new PgpGeneralMsgIdException(R.string.error_expiry_must_come_after_creation);
            }
            hashedPacketsGen.setKeyExpirationTime(false, numDays * 86400);
        } else {
            hashedPacketsGen.setKeyExpirationTime(false, 0);
            // do this explicitly, although since we're rebuilding,
            // this happens anyway
        }

        if (saveParcel.primaryIDChanged ||
                !saveParcel.originalIDs.get(0).equals(saveParcel.userIds.get(0))) {
            anyIDChanged = true;
            ArrayList<Pair<String, PGPSignature>> sigList = new ArrayList<Pair<String, PGPSignature>>();
            for (String userId : saveParcel.userIds) {
                String origID = saveParcel.originalIDs.get(userIDIndex);
                if (origID.equals(userId) && !saveParcel.newIDs[userIDIndex] &&
                        !userId.equals(saveParcel.originalPrimaryID) && userIDIndex != 0) {
                    Iterator<PGPSignature> origSigs = masterPublicKey.getSignaturesForID(origID);
                    // TODO: make sure this iterator only has signatures we are interested in
                    while (origSigs.hasNext()) {
                        PGPSignature origSig = origSigs.next();
                        sigList.add(new Pair<String, PGPSignature>(origID, origSig));
                    }
                } else {
                    PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                            masterPublicKey.getAlgorithm(), HashAlgorithmTags.SHA1)
                            .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                    PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);

                    sGen.init(PGPSignature.POSITIVE_CERTIFICATION, masterPrivateKey);
                    if (userIDIndex == 0) {
                        sGen.setHashedSubpackets(hashedPacketsGen.generate());
                        sGen.setUnhashedSubpackets(unhashedPacketsGen.generate());
                    }
                    PGPSignature certification = sGen.generateCertification(userId, masterPublicKey);
                    sigList.add(new Pair<String, PGPSignature>(userId, certification));
                }
                if (!saveParcel.newIDs[userIDIndex]) {
                    masterPublicKey = PGPPublicKey.removeCertification(masterPublicKey, origID);
                }
                userIDIndex++;
            }
            for (Pair<String, PGPSignature> toAdd : sigList) {
                masterPublicKey =
                        PGPPublicKey.addCertification(masterPublicKey, toAdd.first, toAdd.second);
            }
        } else {
            for (String userId : saveParcel.userIds) {
                String origID = saveParcel.originalIDs.get(userIDIndex);
                if (!origID.equals(userId) || saveParcel.newIDs[userIDIndex]) {
                    anyIDChanged = true;
                    PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                            masterPublicKey.getAlgorithm(), HashAlgorithmTags.SHA1)
                            .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                    PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);

                    sGen.init(PGPSignature.POSITIVE_CERTIFICATION, masterPrivateKey);
                    if (userIDIndex == 0) {
                        sGen.setHashedSubpackets(hashedPacketsGen.generate());
                        sGen.setUnhashedSubpackets(unhashedPacketsGen.generate());
                    }
                    PGPSignature certification = sGen.generateCertification(userId, masterPublicKey);
                    if (!saveParcel.newIDs[userIDIndex]) {
                        masterPublicKey = PGPPublicKey.removeCertification(masterPublicKey, origID);
                    }
                    masterPublicKey =
                            PGPPublicKey.addCertification(masterPublicKey, userId, certification);
                }
                userIDIndex++;
            }
        }

        ArrayList<Pair<String, PGPSignature>> sigList = new ArrayList<Pair<String, PGPSignature>>();
        if (saveParcel.moddedKeys[0]) {
            userIDIndex = 0;
            for (String userId : saveParcel.userIds) {
                String origID = saveParcel.originalIDs.get(userIDIndex);
                if (!(origID.equals(saveParcel.originalPrimaryID) && !saveParcel.primaryIDChanged)) {
                    Iterator<PGPSignature> sigs = masterPublicKey.getSignaturesForID(userId);
                    // TODO: make sure this iterator only has signatures we are interested in
                    while (sigs.hasNext()) {
                        PGPSignature sig = sigs.next();
                        sigList.add(new Pair<String, PGPSignature>(userId, sig));
                    }
                }
                masterPublicKey = PGPPublicKey.removeCertification(masterPublicKey, userId);
                userIDIndex++;
            }
            anyIDChanged = true;
        }

        //update the keyring with the new ID information
        if (anyIDChanged) {
            pKR = PGPPublicKeyRing.insertPublicKey(pKR, masterPublicKey);
            mKR = PGPSecretKeyRing.replacePublicKeys(mKR, pKR);
        }

        PGPKeyPair masterKeyPair = new PGPKeyPair(masterPublicKey, masterPrivateKey);

        updateProgress(R.string.progress_building_master_key, 30, 100);

        // define hashing and signing algos
        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(
                HashAlgorithmTags.SHA1);
        PGPContentSignerBuilder certificationSignerBuilder = new JcaPGPContentSignerBuilder(
                masterKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1);

        // Build key encryptor based on old passphrase, as some keys may be unchanged
        PBESecretKeyEncryptor keyEncryptor = new JcePBESecretKeyEncryptorBuilder(
                PGPEncryptedData.CAST5, sha1Calc)
                .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                        saveParcel.oldPassphrase.toCharArray());

        //this generates one more signature than necessary...
        PGPKeyRingGenerator keyGen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION,
                masterKeyPair, mainUserId, sha1Calc, hashedPacketsGen.generate(),
                unhashedPacketsGen.generate(), certificationSignerBuilder, keyEncryptor);

        for (int i = 1; i < saveParcel.keys.size(); ++i) {
            updateProgress(40 + 50 * i / saveParcel.keys.size(), 100);
            if (saveParcel.moddedKeys[i]) {
                PGPSecretKey subKey = saveParcel.keys.get(i).getSecretKeyExternal();
                PGPPublicKey subPublicKey = subKey.getPublicKey();

                PBESecretKeyDecryptor keyDecryptor2;
                if (saveParcel.newKeys[i]) {
                    keyDecryptor2 = new JcePBESecretKeyDecryptorBuilder()
                            .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                                    "".toCharArray());
                } else {
                    keyDecryptor2 = new JcePBESecretKeyDecryptorBuilder()
                            .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                                    saveParcel.oldPassphrase.toCharArray());
                }
                PGPPrivateKey subPrivateKey = subKey.extractPrivateKey(keyDecryptor2);
                PGPKeyPair subKeyPair = new PGPKeyPair(subPublicKey, subPrivateKey);

                hashedPacketsGen = new PGPSignatureSubpacketGenerator();
                unhashedPacketsGen = new PGPSignatureSubpacketGenerator();

                usageId = saveParcel.keysUsages.get(i);
                canSign = (usageId & KeyFlags.SIGN_DATA) > 0; //todo - separate function for this
                if (canSign) {
                    Date todayDate = new Date(); //both sig times the same
                    // cross-certify signing keys
                    hashedPacketsGen.setSignatureCreationTime(false, todayDate); //set outer creation time
                    PGPSignatureSubpacketGenerator subHashedPacketsGen = new PGPSignatureSubpacketGenerator();
                    subHashedPacketsGen.setSignatureCreationTime(false, todayDate); //set inner creation time
                    PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                            subPublicKey.getAlgorithm(), PGPUtil.SHA1)
                            .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
                    PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);
                    sGen.init(PGPSignature.PRIMARYKEY_BINDING, subPrivateKey);
                    sGen.setHashedSubpackets(subHashedPacketsGen.generate());
                    PGPSignature certification = sGen.generateCertification(masterPublicKey,
                            subPublicKey);
                    unhashedPacketsGen.setEmbeddedSignature(false, certification);
                }
                hashedPacketsGen.setKeyFlags(false, usageId);

                if (saveParcel.keysExpiryDates.get(i) != null) {
                    Calendar creationDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    creationDate.setTime(subPublicKey.getCreationTime());
                    Calendar expiryDate = saveParcel.keysExpiryDates.get(i);
                    // note that the below, (a/c) - (b/c) is *not* the same as (a - b) /c
                    // here we purposefully ignore partial days in each date - long type has
                    // no fractional part!
                    long numDays = (expiryDate.getTimeInMillis() / 86400000) -
                            (creationDate.getTimeInMillis() / 86400000);
                    if (numDays <= 0) {
                        throw new PgpGeneralMsgIdException(R.string.error_expiry_must_come_after_creation);
                    }
                    hashedPacketsGen.setKeyExpirationTime(false, numDays * 86400);
                } else {
                    hashedPacketsGen.setKeyExpirationTime(false, 0);
                    // do this explicitly, although since we're rebuilding,
                    // this happens anyway
                }

                keyGen.addSubKey(subKeyPair, hashedPacketsGen.generate(), unhashedPacketsGen.generate());
                // certifications will be discarded if the key is changed, because I think, for a start,
                // they will be invalid. Binding certs are regenerated anyway, and other certs which
                // need to be kept are on IDs and attributes
                // TODO: don't let revoked keys be edited, other than removed - changing one would
                // result in the revocation being wrong?
            }
        }

        PGPSecretKeyRing updatedSecretKeyRing = keyGen.generateSecretKeyRing();
        //finally, update the keyrings
        Iterator<PGPSecretKey> itr = updatedSecretKeyRing.getSecretKeys();
        while (itr.hasNext()) {
            PGPSecretKey theNextKey = itr.next();
            if ((theNextKey.isMasterKey() && saveParcel.moddedKeys[0]) || !theNextKey.isMasterKey()) {
                mKR = PGPSecretKeyRing.insertSecretKey(mKR, theNextKey);
                pKR = PGPPublicKeyRing.insertPublicKey(pKR, theNextKey.getPublicKey());
            }
        }

        //replace lost IDs
        if (saveParcel.moddedKeys[0]) {
            masterPublicKey = mKR.getPublicKey();
            for (Pair<String, PGPSignature> toAdd : sigList) {
                masterPublicKey = PGPPublicKey.addCertification(masterPublicKey, toAdd.first, toAdd.second);
            }
            pKR = PGPPublicKeyRing.insertPublicKey(pKR, masterPublicKey);
            mKR = PGPSecretKeyRing.replacePublicKeys(mKR, pKR);
        }

        // Build key encryptor based on new passphrase
        PBESecretKeyEncryptor keyEncryptorNew = new JcePBESecretKeyEncryptorBuilder(
                PGPEncryptedData.CAST5, sha1Calc)
                .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                        saveParcel.newPassphrase.toCharArray());

        //update the passphrase
        mKR = PGPSecretKeyRing.copyWithNewPassword(mKR, keyDecryptor, keyEncryptorNew);

        /* additional handy debug info

        Log.d(Constants.TAG, " ------- in private key -------");

        for(String uid : new IterableIterator<String>(secretKeyRing.getPublicKey().getUserIDs())) {
            for(PGPSignature sig : new IterableIterator<PGPSignature>(
                                    secretKeyRing.getPublicKey().getSignaturesForId(uid))) {
                Log.d(Constants.TAG, "sig: " +
                    PgpKeyHelper.convertKeyIdToHex(sig.getKeyID()) + " for " + uid);
             }

        }

        Log.d(Constants.TAG, " ------- in public key -------");

        for(String uid : new IterableIterator<String>(publicKeyRing.getPublicKey().getUserIDs())) {
            for(PGPSignature sig : new IterableIterator<PGPSignature>(
                                    publicKeyRing.getPublicKey().getSignaturesForId(uid))) {
                Log.d(Constants.TAG, "sig: " +
                    PgpKeyHelper.convertKeyIdToHex(sig.getKeyID()) + " for " + uid);
            }
        }

        */

        return new Pair<UncachedKeyRing,UncachedKeyRing>(new UncachedKeyRing(pKR),
                                                         new UncachedKeyRing(mKR));

    }

    public Pair<PGPSecretKeyRing, PGPPublicKeyRing> buildSecretKey(PGPSecretKeyRing sKR,
                                                                   PGPPublicKeyRing pKR,
                                                                   SaveKeyringParcel saveParcel,
                                                                   String passphrase)
            throws PgpGeneralMsgIdException, PGPException, SignatureException, IOException {

        updateProgress(R.string.progress_building_key, 0, 100);

        // sort these, so we can use binarySearch later on
        Arrays.sort(saveParcel.revokeSubKeys);
        Arrays.sort(saveParcel.revokeUserIds);

        /*
         * What's gonna happen here:
         *
         * 1. Unlock private key
         *
         * 2. Create new secret key ring
         *
         * 3. Copy subkeys
         *  - Generate revocation if requested
         *  - Copy old cert, or generate new if change requested
         *
         * 4. Generate and add new subkeys
         *
         * 5. Copy user ids
         *  - Generate revocation if requested
         *  - Copy old cert, or generate new if primary user id status changed
         *
         * 6. Add new user ids
         *
         * 7. Generate PublicKeyRing from SecretKeyRing
         *
         * 8. Return pair (PublicKeyRing,SecretKeyRing)
         *
         */

        // 1. Unlock private key
        updateProgress(R.string.progress_building_key, 0, 100);

        PGPPublicKey masterPublicKey = sKR.getPublicKey();
        PGPPrivateKey masterPrivateKey; {
            PGPSecretKey masterKey = sKR.getSecretKey();
            PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                    Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(passphrase.toCharArray());
            masterPrivateKey = masterKey.extractPrivateKey(keyDecryptor);
        }

        // 2. Create new secret key ring
        updateProgress(R.string.progress_certifying_master_key, 20, 100);

        // Note we do NOT use PGPKeyRingGeneraor, it's just one level too high and does stuff
        // we want to do manually. Instead, we simply use a list of secret keys.
        ArrayList<PGPSecretKey> secretKeys = new ArrayList<PGPSecretKey>();
        ArrayList<PGPPublicKey> publicKeys = new ArrayList<PGPPublicKey>();

        // 3. Copy subkeys
        // - Generate revocation if requested
        // - Copy old cert, or generate new if change requested
        for (PGPSecretKey sKey : new IterableIterator<PGPSecretKey>(sKR.getSecretKeys())) {
            PGPPublicKey pKey = sKey.getPublicKey();
            if (Arrays.binarySearch(saveParcel.revokeSubKeys, sKey.getKeyID()) >= 0) {
                // add revocation signature to key, if there is none yet
                if (!pKey.getSignaturesOfType(PGPSignature.SUBKEY_REVOCATION).hasNext()) {
                    // generate revocation signature
                }
            }
            if (saveParcel.changeSubKeys.containsKey(sKey.getKeyID())) {
                // change subkey flags?
                SaveKeyringParcel.SubkeyChange change = saveParcel.changeSubKeys.get(sKey.getKeyID());
                // remove old subkey binding signature(s?)
                for (PGPSignature sig : new IterableIterator<PGPSignature>(
                        pKey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING))) {
                    pKey = PGPPublicKey.removeCertification(pKey, sig);
                }

                // generate and add new signature
                PGPSignature sig = generateSubkeyBindingSignature(masterPublicKey, masterPrivateKey,
                        sKey, pKey, change.mFlags, change.mExpiry, passphrase);
                pKey = PGPPublicKey.addCertification(pKey, sig);
            }
            secretKeys.add(PGPSecretKey.replacePublicKey(sKey, pKey));
            publicKeys.add(pKey);
        }

        // 4. Generate and add new subkeys
        // TODO

        // 5. Copy user ids
        for (String userId : new IterableIterator<String>(masterPublicKey.getUserIDs())) {
            // - Copy old cert, or generate new if primary user id status changed
            boolean certified = false, revoked = false;
            for (PGPSignature sig : new IterableIterator<PGPSignature>(
                    masterPublicKey.getSignaturesForID(userId))) {
                // We know there are only revocation and certification types in here.
                switch(sig.getSignatureType()) {
                    case PGPSignature.CERTIFICATION_REVOCATION:
                        revoked = true;
                        continue;

                    case PGPSignature.DEFAULT_CERTIFICATION:
                    case PGPSignature.NO_CERTIFICATION:
                    case PGPSignature.CASUAL_CERTIFICATION:
                    case PGPSignature.POSITIVE_CERTIFICATION:
                        // Already got one? Remove this one, then.
                        if (certified) {
                            masterPublicKey = PGPPublicKey.removeCertification(
                                    masterPublicKey, userId, sig);
                            continue;
                        }
                        boolean primary = userId.equals(saveParcel.changePrimaryUserId);
                        // Generate a new one under certain circumstances
                        if (saveParcel.changePrimaryUserId != null &&
                                sig.getHashedSubPackets().isPrimaryUserID() != primary) {
                            PGPSignature cert = generateUserIdSignature(
                                    masterPrivateKey, masterPublicKey, userId, primary);
                            PGPPublicKey.addCertification(masterPublicKey, userId, cert);
                        }
                        certified = true;
                }
            }
            // - Generate revocation if requested
            if (!revoked && Arrays.binarySearch(saveParcel.revokeUserIds, userId) >= 0) {
                PGPSignature cert = generateRevocationSignature(masterPrivateKey,
                        masterPublicKey, userId);
                masterPublicKey = PGPPublicKey.addCertification(masterPublicKey, userId, cert);
            }
        }

        // 6. Add new user ids
        for(String userId : saveParcel.addUserIds) {
            PGPSignature cert = generateUserIdSignature(masterPrivateKey,
                    masterPublicKey, userId, userId.equals(saveParcel.changePrimaryUserId));
            masterPublicKey = PGPPublicKey.addCertification(masterPublicKey, userId, cert);
        }

        // 7. Generate PublicKeyRing from SecretKeyRing
        updateProgress(R.string.progress_building_master_key, 30, 100);
        PGPSecretKeyRing ring = new PGPSecretKeyRing(secretKeys);

        // Copy all non-self uid certificates
        for (String userId : new IterableIterator<String>(masterPublicKey.getUserIDs())) {
            // - Copy old cert, or generate new if primary user id status changed
            boolean certified = false, revoked = false;
            for (PGPSignature sig : new IterableIterator<PGPSignature>(
                    masterPublicKey.getSignaturesForID(userId))) {
            }
        }

        for (PGPPublicKey newKey : publicKeys) {
            PGPPublicKey oldKey = pKR.getPublicKey(newKey.getKeyID());
            for (PGPSignature sig : new IterableIterator<PGPSignature>(
                    oldKey.getSignatures())) {
            }
        }

        // If requested, set new passphrase
        if (saveParcel.newPassphrase != null) {
            PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build()
                    .get(HashAlgorithmTags.SHA1);
            PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                    Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(passphrase.toCharArray());
            // Build key encryptor based on new passphrase
            PBESecretKeyEncryptor keyEncryptorNew = new JcePBESecretKeyEncryptorBuilder(
                    PGPEncryptedData.CAST5, sha1Calc)
                    .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                            saveParcel.newPassphrase.toCharArray());

            sKR = PGPSecretKeyRing.copyWithNewPassword(sKR, keyDecryptor, keyEncryptorNew);
        }

        // 8. Return pair (PublicKeyRing,SecretKeyRing)

        return new Pair<PGPSecretKeyRing, PGPPublicKeyRing>(sKR, pKR);

    }

    private static PGPSignature generateUserIdSignature(
            PGPPrivateKey masterPrivateKey, PGPPublicKey pKey, String userId, boolean primary)
            throws IOException, PGPException, SignatureException {
        PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                pKey.getAlgorithm(), PGPUtil.SHA1)
                .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
        PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);
        PGPSignatureSubpacketGenerator subHashedPacketsGen = new PGPSignatureSubpacketGenerator();
        subHashedPacketsGen.setSignatureCreationTime(false, new Date());
        subHashedPacketsGen.setPreferredSymmetricAlgorithms(true, PREFERRED_SYMMETRIC_ALGORITHMS);
        subHashedPacketsGen.setPreferredHashAlgorithms(true, PREFERRED_HASH_ALGORITHMS);
        subHashedPacketsGen.setPreferredCompressionAlgorithms(true, PREFERRED_COMPRESSION_ALGORITHMS);
        subHashedPacketsGen.setPrimaryUserID(false, primary);
        sGen.setHashedSubpackets(subHashedPacketsGen.generate());
        sGen.init(PGPSignature.POSITIVE_CERTIFICATION, masterPrivateKey);
        return sGen.generateCertification(userId, pKey);
    }

    private static PGPSignature generateRevocationSignature(
            PGPPrivateKey masterPrivateKey, PGPPublicKey pKey, String userId)
        throws IOException, PGPException, SignatureException {
        PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                pKey.getAlgorithm(), PGPUtil.SHA1)
                .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
        PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);
        PGPSignatureSubpacketGenerator subHashedPacketsGen = new PGPSignatureSubpacketGenerator();
        subHashedPacketsGen.setSignatureCreationTime(false, new Date());
        sGen.setHashedSubpackets(subHashedPacketsGen.generate());
        sGen.init(PGPSignature.CERTIFICATION_REVOCATION, masterPrivateKey);
        return sGen.generateCertification(userId, pKey);
    }

    private static PGPSignature generateSubkeyBindingSignature(
            PGPPublicKey masterPublicKey, PGPPrivateKey masterPrivateKey,
            PGPSecretKey sKey, PGPPublicKey pKey,
            int flags, Long expiry, String passphrase)
            throws PgpGeneralMsgIdException, IOException, PGPException, SignatureException {

        // date for signing
        Date todayDate = new Date();
        PGPSignatureSubpacketGenerator unhashedPacketsGen = new PGPSignatureSubpacketGenerator();

        // If this key can sign, we need a primary key binding signature
        if ((flags & KeyFlags.SIGN_DATA) != 0) {

            PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder()
                    .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(
                            passphrase.toCharArray());
            PGPPrivateKey subPrivateKey = sKey.extractPrivateKey(keyDecryptor);

            // cross-certify signing keys
            PGPSignatureSubpacketGenerator subHashedPacketsGen = new PGPSignatureSubpacketGenerator();
            subHashedPacketsGen.setSignatureCreationTime(false, todayDate);
            PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                    pKey.getAlgorithm(), PGPUtil.SHA1)
                    .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
            PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);
            sGen.init(PGPSignature.PRIMARYKEY_BINDING, subPrivateKey);
            sGen.setHashedSubpackets(subHashedPacketsGen.generate());
            PGPSignature certification = sGen.generateCertification(masterPublicKey, pKey);
            unhashedPacketsGen.setEmbeddedSignature(false, certification);
        }

        PGPSignatureSubpacketGenerator hashedPacketsGen;
        {
            hashedPacketsGen = new PGPSignatureSubpacketGenerator();
            hashedPacketsGen.setSignatureCreationTime(false, todayDate);
            hashedPacketsGen.setKeyFlags(false, flags);
        }

        if (expiry != null) {
            Calendar creationDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            creationDate.setTime(pKey.getCreationTime());
            // note that the below, (a/c) - (b/c) is *not* the same as (a - b) /c
            // here we purposefully ignore partial days in each date - long type has
            // no fractional part!
            long numDays = (expiry / 86400000) -
                    (creationDate.getTimeInMillis() / 86400000);
            if (numDays <= 0) {
                throw new PgpGeneralMsgIdException(R.string.error_expiry_must_come_after_creation);
            }
            hashedPacketsGen.setKeyExpirationTime(false, expiry - creationDate.getTimeInMillis());
        } else {
            hashedPacketsGen.setKeyExpirationTime(false, 0);
        }

        PGPContentSignerBuilder signerBuilder = new JcaPGPContentSignerBuilder(
                pKey.getAlgorithm(), PGPUtil.SHA1)
                .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);
        PGPSignatureGenerator sGen = new PGPSignatureGenerator(signerBuilder);
        sGen.init(PGPSignature.SUBKEY_BINDING, masterPrivateKey);
        sGen.setHashedSubpackets(hashedPacketsGen.generate());
        sGen.setUnhashedSubpackets(unhashedPacketsGen.generate());

        return sGen.generateCertification(masterPublicKey, pKey);

    }


    /**
     * Certify the given pubkeyid with the given masterkeyid.
     *
     * @param certificationKey Certifying key
     * @param publicKey        public key to certify
     * @param userIds          User IDs to certify, must not be null or empty
     * @param passphrase       Passphrase of the secret key
     * @return A keyring with added certifications
     */
    public PGPPublicKey certifyKey(PGPSecretKey certificationKey, PGPPublicKey publicKey,
                                   List<String> userIds, String passphrase)
            throws PgpGeneralMsgIdException, NoSuchAlgorithmException, NoSuchProviderException,
            PGPException, SignatureException {

        // create a signatureGenerator from the supplied masterKeyId and passphrase
        PGPSignatureGenerator signatureGenerator;
        {

            if (certificationKey == null) {
                throw new PgpGeneralMsgIdException(R.string.error_no_signature_key);
            }

            PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                    Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(passphrase.toCharArray());
            PGPPrivateKey signaturePrivateKey = certificationKey.extractPrivateKey(keyDecryptor);
            if (signaturePrivateKey == null) {
                throw new PgpGeneralMsgIdException(R.string.error_could_not_extract_private_key);
            }

            // TODO: SHA256 fixed?
            JcaPGPContentSignerBuilder contentSignerBuilder = new JcaPGPContentSignerBuilder(
                    certificationKey.getPublicKey().getAlgorithm(), PGPUtil.SHA256)
                    .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);

            signatureGenerator = new PGPSignatureGenerator(contentSignerBuilder);
            signatureGenerator.init(PGPSignature.DEFAULT_CERTIFICATION, signaturePrivateKey);
        }

        { // supply signatureGenerator with a SubpacketVector
            PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
            PGPSignatureSubpacketVector packetVector = spGen.generate();
            signatureGenerator.setHashedSubpackets(packetVector);
        }

        // fetch public key ring, add the certification and return it
        for (String userId : new IterableIterator<String>(userIds.iterator())) {
            PGPSignature sig = signatureGenerator.generateCertification(userId, publicKey);
            publicKey = PGPPublicKey.addCertification(publicKey, userId, sig);
        }

        return publicKey;
    }


    /**
     * Revoke the given key
     *
     * @param certificationKey      key to revoke
     * @param passphrase       Passphrase of the secret key
     * @param reason           revokation reason
     * @param description      Revokation description
     * @return A keyring with added certifications
     */
    public PGPSignature revokeKey(PGPSecretKey certificationKey, byte reason, String description,
                                  String passphrase)
            throws PgpGeneralMsgIdException, NoSuchAlgorithmException, NoSuchProviderException,
            PGPException, SignatureException {

        PGPSignatureGenerator signatureGenerator;

        if (certificationKey == null) {
            throw new PgpGeneralMsgIdException(R.string.error_no_signature_key);
        }

        PBESecretKeyDecryptor keyDecryptor = new JcePBESecretKeyDecryptorBuilder().setProvider(
                Constants.BOUNCY_CASTLE_PROVIDER_NAME).build(passphrase.toCharArray());
        PGPPrivateKey signaturePrivateKey = certificationKey.extractPrivateKey(keyDecryptor);
        if (signaturePrivateKey == null) {
            throw new PgpGeneralMsgIdException(R.string.error_could_not_extract_private_key);
        }

        JcaPGPContentSignerBuilder contentSignerBuilder = new JcaPGPContentSignerBuilder(
            certificationKey.getPublicKey().getAlgorithm(), PGPUtil.SHA256)
            .setProvider(Constants.BOUNCY_CASTLE_PROVIDER_NAME);

        signatureGenerator = new PGPSignatureGenerator(contentSignerBuilder);
        signatureGenerator.init(PGPSignature.KEY_REVOCATION, signaturePrivateKey);


        // supply signatureGenerator with a SubpacketVector
        PGPSignatureSubpacketGenerator hashed = new PGPSignatureSubpacketGenerator();
        hashed.setSignatureCreationTime(false, new Date());
        hashed.setRevocationReason(false, reason, description);

        PGPSignatureSubpacketGenerator unhashed = new PGPSignatureSubpacketGenerator();
        unhashed.setIssuerKeyID(false, certificationKey.getPublicKey().getKeyID());

        signatureGenerator.setHashedSubpackets(hashed.generate());
        signatureGenerator.setUnhashedSubpackets(unhashed.generate());


        return signatureGenerator.generateCertification(certificationKey.getPublicKey());


    }

    /**
     * Simple static subclass that stores two values.
     * <p/>
     * This is only used to return a pair of values in one function above. We specifically don't use
     * com.android.Pair to keep this class free from android dependencies.
     */
    public static class Pair<K, V> {
        public final K first;
        public final V second;

        public Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }
    }
}

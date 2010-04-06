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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Vector;

import org.bouncycastle2.openpgp.PGPException;
import org.bouncycastle2.openpgp.PGPSecretKey;
import org.bouncycastle2.openpgp.PGPSecretKeyRing;
import org.thialfihar.android.apg.utils.IterableIterator;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnKeyListener;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnChildClickListener;

public class SecretKeyListActivity extends ExpandableListActivity
                                   implements Runnable, ProgressDialogUpdater, OnChildClickListener,
                                   AskForSecretKeyPassPhrase.PassPhraseCallbackInterface {
    static final int CREATE_SECRET_KEY = 1;
    static final int EDIT_SECRET_KEY = 2;

    static final int MENU_EDIT = 1;
    static final int MENU_EXPORT = 2;
    static final int MENU_DELETE = 3;

    static final int OPTION_MENU_IMPORT_KEYS = 1;
    static final int OPTION_MENU_EXPORT_KEYS = 2;
    static final int OPTION_MENU_CREATE_KEY = 3;

    static final int MESSAGE_PROGRESS_UPDATE = 1;
    static final int MESSAGE_DONE = 2;
    static final int MESSAGE_IMPORT_DONE = 2;
    static final int MESSAGE_EXPORT_DONE = 3;

    static final int DIALOG_DELETE_KEY = 1;
    static final int DIALOG_IMPORT_KEYS = 2;
    static final int DIALOG_IMPORTING = 3;
    static final int DIALOG_EXPORT_KEYS = 4;
    static final int DIALOG_EXPORTING = 5;
    static final int DIALOG_EXPORT_KEY = 6;

    static final int TASK_IMPORT = 1;
    static final int TASK_EXPORT = 2;

    protected int mSelectedItem = -1;
    protected String mImportFilename = null;
    protected String mExportFilename = null;
    protected int mTask = 0;

    private ProgressDialog mProgressDialog = null;
    private Thread mRunningThread = null;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            if (data != null) {
                int type = data.getInt("type");
                switch (type) {
                    case MESSAGE_PROGRESS_UPDATE: {
                        String message = data.getString("message");
                        if (mProgressDialog != null) {
                            if (message != null) {
                                mProgressDialog.setMessage(message);
                            }
                            mProgressDialog.setMax(data.getInt("max"));
                            mProgressDialog.setProgress(data.getInt("progress"));
                        }
                        break;
                    }

                    case MESSAGE_IMPORT_DONE: {
                        removeDialog(DIALOG_IMPORTING);
                        mProgressDialog = null;

                        String error = data.getString("error");
                        if (error != null) {
                            Toast.makeText(SecretKeyListActivity.this,
                                           "Error: " + data.getString("error"),
                                           Toast.LENGTH_SHORT).show();
                        } else {
                            int added = data.getInt("added");
                            int updated = data.getInt("updated");
                            String message;
                            if (added > 0 && updated > 0) {
                                message = "Succssfully added " + added + " keys and updated " +
                                          updated + " keys.";
                            } else if (added > 0) {
                                message = "Succssfully added " + added + " keys.";
                            } else if (updated > 0) {
                                message = "Succssfully updated " + updated + " keys.";
                            } else {
                                message = "No keys added or updated.";
                            }
                            Toast.makeText(SecretKeyListActivity.this, message,
                                           Toast.LENGTH_SHORT).show();
                        }
                        refreshList();
                        break;
                    }

                    case MESSAGE_EXPORT_DONE: {
                        removeDialog(DIALOG_EXPORTING);
                        mProgressDialog = null;

                        String error = data.getString("error");
                        if (error != null) {
                            Toast.makeText(SecretKeyListActivity.this,
                                           "Error: " + data.getString("error"),
                                           Toast.LENGTH_SHORT).show();
                        } else {
                            int exported = data.getInt("exported");
                            String message;
                            if (exported == 1) {
                                message = "Succssfully exported 1 key.";
                            } else if (exported > 0) {
                                message = "Succssfully exported " + exported + " keys.";
                            } else{
                                message = "No keys exported.";
                            }
                            Toast.makeText(SecretKeyListActivity.this, message,
                                           Toast.LENGTH_SHORT).show();
                        }
                        break;
                    }

                    default: {
                        break;
                    }
                }
            }
        }
    };

    public void setProgress(int progress, int max) {
        Message msg = new Message();
        Bundle data = new Bundle();
        data.putInt("type", MESSAGE_PROGRESS_UPDATE);
        data.putInt("progress", progress);
        data.putInt("max", max);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    public void setProgress(String message, int progress, int max) {
        Message msg = new Message();
        Bundle data = new Bundle();
        data.putInt("type", MESSAGE_PROGRESS_UPDATE);
        data.putString("message", message);
        data.putInt("progress", progress);
        data.putInt("max", max);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Apg.initialize(this);

        setListAdapter(new SecretKeyListAdapter(this));
        registerForContextMenu(getExpandableListView());
        getExpandableListView().setOnChildClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, OPTION_MENU_IMPORT_KEYS, 0, "Import Keys")
                .setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, OPTION_MENU_EXPORT_KEYS, 1, "Export Keys")
                .setIcon(android.R.drawable.ic_menu_save);
        menu.add(1, OPTION_MENU_CREATE_KEY, 2, "Create Key")
                .setIcon(android.R.drawable.ic_menu_add);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTION_MENU_IMPORT_KEYS: {
                showDialog(DIALOG_IMPORT_KEYS);
                return true;
            }

            case OPTION_MENU_EXPORT_KEYS: {
                showDialog(DIALOG_EXPORT_KEYS);
                return true;
            }

            case OPTION_MENU_CREATE_KEY: {
                createKey();
                return true;
            }

            default: {
                break;
            }
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        ExpandableListView.ExpandableListContextMenuInfo info =
                (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        int groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);

        if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
            PGPSecretKeyRing keyRing = Apg.getSecretKeyRings().get(groupPosition);
            String userId = Apg.getMainUserIdSafe(this, Apg.getMasterKey(keyRing));
            menu.setHeaderTitle(userId);
            menu.add(0, MENU_EDIT, 0, "Edit Key");
            menu.add(0, MENU_EXPORT, 1, "Export Key");
            menu.add(0, MENU_DELETE, 2, "Delete Key");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuItem.getMenuInfo();
        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        int groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);

        if (type != ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
            return super.onContextItemSelected(menuItem);
        }

        switch (menuItem.getItemId()) {
            case MENU_EDIT: {
                mSelectedItem = groupPosition;
                showDialog(AskForSecretKeyPassPhrase.DIALOG_PASS_PHRASE);
                return true;
            }

            case MENU_EXPORT: {
                mSelectedItem = groupPosition;
                showDialog(DIALOG_EXPORT_KEY);
                return true;
            }

            case MENU_DELETE: {
                mSelectedItem = groupPosition;
                showDialog(DIALOG_DELETE_KEY);
                return true;
            }

            default: {
                return super.onContextItemSelected(menuItem);
            }
        }
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                int childPosition, long id) {
        mSelectedItem = groupPosition;
        showDialog(AskForSecretKeyPassPhrase.DIALOG_PASS_PHRASE);
        return true;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        boolean singleKeyExport = false;

        switch (id) {
            case DIALOG_DELETE_KEY: {
                PGPSecretKeyRing keyRing = Apg.getSecretKeyRings().get(mSelectedItem);

                String userId = Apg.getMainUserIdSafe(this, Apg.getMasterKey(keyRing));

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Warning  ");
                builder.setMessage("Do you really want to delete the key '" + userId + "'?\n" +
                                   "You can't undo this!");
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                              public void onClick(DialogInterface dialog, int id) {
                                                  deleteKey(mSelectedItem);
                                                  mSelectedItem = -1;
                                                  removeDialog(DIALOG_DELETE_KEY);
                                              }
                                          });
                builder.setNegativeButton(android.R.string.ok,
                                          new DialogInterface.OnClickListener() {
                                              public void onClick(DialogInterface dialog, int id) {
                                                  mSelectedItem = -1;
                                                  removeDialog(DIALOG_DELETE_KEY);
                                              }
                                          });
                return builder.create();
            }

            case DIALOG_IMPORT_KEYS: {
                AlertDialog.Builder alert = new AlertDialog.Builder(this);

                alert.setTitle("Import Keys");
                alert.setMessage("Please specify which file to import from.");

                final EditText input = new EditText(this);
                // TODO: default file?
                input.setText(Environment.getExternalStorageDirectory() + "/secring.gpg");
                input.setOnKeyListener(new OnKeyListener() {
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        // TODO: this doesn't actually work yet
                        // If the event is a key-down event on the "enter"
                        // button
                        if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                            (keyCode == KeyEvent.KEYCODE_ENTER)) {
                            try {
                                ((AlertDialog) v.getParent())
                                        .getButton(AlertDialog.BUTTON_POSITIVE)
                                        .performClick();
                            } catch (ClassCastException e) {
                                // don't do anything if we're not in that dialog
                            }
                            return true;
                        }
                        return false;
                    }
                });
                alert.setView(input);

                alert.setPositiveButton(android.R.string.ok,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                removeDialog(DIALOG_IMPORT_KEYS);
                                                mImportFilename = input.getText().toString();
                                                importKeys();
                                            }
                                        });

                alert.setNegativeButton(android.R.string.cancel,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                removeDialog(DIALOG_IMPORT_KEYS);
                                            }
                                        });
                return alert.create();
            }

            case DIALOG_EXPORT_KEY: {
                singleKeyExport = true;
                // break intentionally omitted, to use the DIALOG_EXPORT_KEYS dialog
            }

            case DIALOG_EXPORT_KEYS: {
                AlertDialog.Builder alert = new AlertDialog.Builder(this);

                if (singleKeyExport) {
                    alert.setTitle("Export Key");
                } else {
                    alert.setTitle("Export Keys");
                    mSelectedItem = -1;
                }
                final int thisDialogId = (singleKeyExport ? DIALOG_DELETE_KEY : DIALOG_EXPORT_KEYS);
                alert.setMessage("Please specify which file to export to.\n" +
                                 "WARNING! You are about to export a SECRET key.\n" +
                                 "WARNING! File will be overwritten if it exists.");

                final EditText input = new EditText(this);
                // TODO: default file?
                input.setText(Environment.getExternalStorageDirectory() + "/secexport.asc");
                input.setOnKeyListener(new OnKeyListener() {
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        // TODO: this doesn't actually work yet
                        // If the event is a key-down event on the "enter"
                        // button
                        if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                            (keyCode == KeyEvent.KEYCODE_ENTER)) {
                            try {
                                ((AlertDialog) v.getParent())
                                        .getButton(AlertDialog.BUTTON_POSITIVE)
                                        .performClick();
                            } catch (ClassCastException e) {
                                // don't do anything if we're not in that dialog
                            }
                            return true;
                        }
                        return false;
                    }
                });
                alert.setView(input);

                alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                removeDialog(thisDialogId);
                                                mExportFilename = input.getText().toString();
                                                exportKeys();
                                            }
                                        });

                alert.setNegativeButton(android.R.string.cancel,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                removeDialog(thisDialogId);
                                            }
                                        });
                return alert.create();
            }

            case DIALOG_IMPORTING: {
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setMessage("importing...");
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(false);
                return mProgressDialog;
            }

            case DIALOG_EXPORTING: {
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setMessage("exporting...");
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setCancelable(false);
                return mProgressDialog;
            }

            case AskForSecretKeyPassPhrase.DIALOG_PASS_PHRASE: {
                PGPSecretKeyRing keyRing = Apg.getSecretKeyRings().get(mSelectedItem);
                long keyId = keyRing.getSecretKey().getKeyID();
                return AskForSecretKeyPassPhrase.createDialog(this, keyId, this);
            }
        }
        return super.onCreateDialog(id);
    }

    public void passPhraseCallback(String passPhrase) {
        Apg.setPassPhrase(passPhrase);
        editKey();
    }

    private void createKey() {
        Intent intent = new Intent(this, EditKeyActivity.class);
        startActivityForResult(intent, CREATE_SECRET_KEY);
    }

    private void editKey() {
        PGPSecretKeyRing keyRing = Apg.getSecretKeyRings().get(mSelectedItem);
        long keyId = keyRing.getSecretKey().getKeyID();
        Intent intent = new Intent(this, EditKeyActivity.class);
        intent.putExtra("keyId", keyId);
        startActivityForResult(intent, EDIT_SECRET_KEY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CREATE_SECRET_KEY: // intentionally no break
            case EDIT_SECRET_KEY: {
                if (resultCode == RESULT_OK) {
                    refreshList();
                }
                break;
            }

            default:
                break;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void importKeys() {
        showDialog(DIALOG_IMPORTING);
        mTask = TASK_IMPORT;
        mRunningThread = new Thread(this);
        mRunningThread.start();
    }

    public void exportKeys() {
        showDialog(DIALOG_EXPORTING);
        mTask = TASK_EXPORT;
        mRunningThread = new Thread(this);
        mRunningThread.start();
    }

    public void run() {
        String error = null;
        Bundle data = new Bundle();
        Message msg = new Message();

        String filename = null;
        if (mTask == TASK_IMPORT) {
            filename = mImportFilename;
        } else {
            filename = mExportFilename;
        }

        try {
            if (mTask == TASK_IMPORT) {
                data = Apg.importKeyRings(this, Apg.TYPE_SECRET, filename, this);
            } else {
                Vector<Object> keys = new Vector<Object>();
                if (mSelectedItem == -1) {
                    for (PGPSecretKeyRing key : Apg.getSecretKeyRings()) {
                        keys.add(key);
                    }
                } else {
                    keys.add(Apg.getSecretKeyRings().get(mSelectedItem));
                }
                data = Apg.exportKeyRings(this, keys, filename, this);
            }
        } catch (FileNotFoundException e) {
            error = "file '" + filename + "' not found";
        } catch (IOException e) {
            error = e.getMessage();
        } catch (PGPException e) {
            error = e.getMessage();
        } catch (Apg.GeneralException e) {
            error = e.getMessage();
        }

        if (mTask == TASK_IMPORT) {
            data.putInt("type", MESSAGE_IMPORT_DONE);
        } else {
            data.putInt("type", MESSAGE_EXPORT_DONE);
        }

        if (error != null) {
            data.putString("error", error);
        }

        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void deleteKey(int index) {
        PGPSecretKeyRing keyRing = Apg.getSecretKeyRings().get(index);
        Apg.deleteKey(this, keyRing);
        refreshList();
    }

    private void refreshList() {
        ((SecretKeyListAdapter) getExpandableListAdapter())
                .notifyDataSetChanged();
    }

    private class SecretKeyListAdapter extends BaseExpandableListAdapter {
        private LayoutInflater mInflater;

        private class KeyChild {
            static final int KEY = 0;
            static final int USER_ID = 1;

            public int type;
            public PGPSecretKey key;
            public String userId;

            public KeyChild(PGPSecretKey key) {
                type = KEY;
                this.key = key;
            }

            public KeyChild(String userId) {
                type = USER_ID;
                this.userId = userId;
            }
        }

        public SecretKeyListAdapter(Context context) {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        protected Vector<KeyChild> getChildrenOfKeyRing(PGPSecretKeyRing keyRing) {
            Vector<KeyChild> children = new Vector<KeyChild>();
            PGPSecretKey masterKey = null;
            for (PGPSecretKey key : new IterableIterator<PGPSecretKey>(keyRing.getSecretKeys())) {
                children.add(new KeyChild(key));
                if (key.isMasterKey()) {
                    masterKey = key;
                }
            }

            if (masterKey != null) {
                boolean isFirst = true;
                for (String userId : new IterableIterator<String>(masterKey.getUserIDs())) {
                    if (isFirst) {
                        // ignore first, it's in the group already
                        isFirst = false;
                        continue;
                    }
                    children.add(new KeyChild(userId));
                }
            }

            return children;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        public int getGroupCount() {
            return Apg.getSecretKeyRings().size();
        }

        public Object getChild(int groupPosition, int childPosition) {
            PGPSecretKeyRing keyRing = Apg.getSecretKeyRings().get(groupPosition);
            Vector<KeyChild> children = getChildrenOfKeyRing(keyRing);
            KeyChild child = children.get(childPosition);
            return child;
        }

        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        public int getChildrenCount(int groupPosition) {
            return getChildrenOfKeyRing(Apg.getSecretKeyRings().get(groupPosition)).size();
        }

        public Object getGroup(int position) {
            return position;
        }

        public long getGroupId(int position) {
            return position;
        }

        public View getGroupView(int groupPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent) {
            PGPSecretKeyRing keyRing = Apg.getSecretKeyRings().get(groupPosition);
            for (PGPSecretKey key : new IterableIterator<PGPSecretKey>(keyRing.getSecretKeys())) {
                View view;
                if (!key.isMasterKey()) {
                    continue;
                }
                view = mInflater.inflate(R.layout.key_list_group_item, null);
                view.setBackgroundResource(android.R.drawable.list_selector_background);

                TextView mainUserId = (TextView) view.findViewById(R.id.main_user_id);
                mainUserId.setText("");
                TextView mainUserIdRest = (TextView) view.findViewById(R.id.main_user_id_rest);
                mainUserIdRest.setText("");

                String userId = Apg.getMainUserId(key);
                if (userId != null) {
                    String chunks[] = userId.split(" <", 2);
                    userId = chunks[0];
                    if (chunks.length > 1) {
                        mainUserIdRest.setText("<" + chunks[1]);
                    }
                    mainUserId.setText(userId);
                }

                if (mainUserId.getText().length() == 0) {
                    mainUserId.setText(R.string.unknown_user_id);
                }

                if (mainUserIdRest.getText().length() == 0) {
                    mainUserIdRest.setVisibility(View.GONE);
                }
                return view;
            }
            return null;
        }

        public View getChildView(int groupPosition, int childPosition,
                                 boolean isLastChild, View convertView,
                                 ViewGroup parent) {
            PGPSecretKeyRing keyRing = Apg.getSecretKeyRings().get(groupPosition);
            Vector<KeyChild> children = getChildrenOfKeyRing(keyRing);

            KeyChild child = children.get(childPosition);
            View view = null;
            switch (child.type) {
                case KeyChild.KEY: {
                    PGPSecretKey key = child.key;
                    if (key.isMasterKey()) {
                        view = mInflater.inflate(R.layout.key_list_child_item_master_key, null);
                    } else {
                        view = mInflater.inflate(R.layout.key_list_child_item_sub_key, null);
                    }

                    TextView keyId = (TextView) view.findViewById(R.id.key_id);
                    String keyIdStr = Long.toHexString(key.getKeyID() & 0xffffffffL);
                    while (keyIdStr.length() < 8) {
                        keyIdStr = "0" + keyIdStr;
                    }
                    keyId.setText(keyIdStr);
                    TextView keyDetails = (TextView) view.findViewById(R.id.key_details);
                    String algorithmStr = Apg.getAlgorithmInfo(key);
                    keyDetails.setText("(" + algorithmStr + ")");

                    ImageView encryptIcon = (ImageView) view.findViewById(R.id.ic_encrypt_key);
                    if (!Apg.isEncryptionKey(key)) {
                        encryptIcon.setVisibility(View.GONE);
                    }

                    ImageView signIcon = (ImageView) view.findViewById(R.id.ic_sign_key);
                    if (!Apg.isSigningKey(key)) {
                        signIcon.setVisibility(View.GONE);
                    }
                    break;
                }

                case KeyChild.USER_ID: {
                    view = mInflater.inflate(R.layout.key_list_child_item_user_id, null);
                    TextView userId = (TextView) view.findViewById(R.id.user_id);
                    userId.setText(child.userId);
                    break;
                }
            }
            return view;
        }
    }
}
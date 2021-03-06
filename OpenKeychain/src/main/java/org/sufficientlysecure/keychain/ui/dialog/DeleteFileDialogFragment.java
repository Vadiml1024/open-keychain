/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.ui.dialog;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.service.KeychainIntentService;
import org.sufficientlysecure.keychain.service.KeychainIntentServiceHandler;

public class DeleteFileDialogFragment extends DialogFragment {
    private static final String ARG_DELETE_FILE = "delete_file";

    /**
     * Creates new instance of this delete file dialog fragment
     */
    public static DeleteFileDialogFragment newInstance(String deleteFile) {
        DeleteFileDialogFragment frag = new DeleteFileDialogFragment();
        Bundle args = new Bundle();

        args.putString(ARG_DELETE_FILE, deleteFile);

        frag.setArguments(args);

        return frag;
    }

    /**
     * Creates dialog
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final FragmentActivity activity = getActivity();

        final String deleteFile = getArguments().getString(ARG_DELETE_FILE);

        CustomAlertDialogBuilder alert = new CustomAlertDialogBuilder(activity);


        alert.setIcon(R.drawable.ic_dialog_alert_holo_light);
        alert.setTitle(R.string.warning);
        alert.setMessage(this.getString(R.string.file_delete_confirmation, deleteFile));

        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int id) {
                dismiss();

                // Send all information needed to service to edit key in other thread
                Intent intent = new Intent(activity, KeychainIntentService.class);

                // fill values for this action
                Bundle data = new Bundle();

                intent.setAction(KeychainIntentService.ACTION_DELETE_FILE_SECURELY);
                data.putString(KeychainIntentService.DELETE_FILE, deleteFile);
                intent.putExtra(KeychainIntentService.EXTRA_DATA, data);

                ProgressDialogFragment deletingDialog = ProgressDialogFragment.newInstance(
                        getString(R.string.progress_deleting_securely),
                        ProgressDialog.STYLE_HORIZONTAL,
                        false,
                        null);

                // Message is received after deleting is done in KeychainIntentService
                KeychainIntentServiceHandler saveHandler =
                        new KeychainIntentServiceHandler(activity, deletingDialog) {
                    public void handleMessage(Message message) {
                        // handle messages by standard KeychainIntentHandler first
                        super.handleMessage(message);

                        if (message.arg1 == KeychainIntentServiceHandler.MESSAGE_OKAY) {
                            Toast.makeText(activity, R.string.file_delete_successful,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                };

                // Create a new Messenger for the communication back
                Messenger messenger = new Messenger(saveHandler);
                intent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);

                // show progress dialog
                deletingDialog.show(activity.getSupportFragmentManager(), "deletingDialog");

                // start service with intent
                activity.startService(intent);
            }
        });
        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dismiss();
            }
        });
        alert.setCancelable(true);

        return alert.show();
    }
}

package com.emuneee.baecon;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.Strategy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BaeconActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, View.OnClickListener, Dialog.OnClickListener {

    private static final String TAG = "BaeconActivity";
    private static final int REQUEST_RESOLVE_ERROR = -12389;

    private GoogleApiClient apiClient;
    private boolean mResolvingError;
    private RecyclerView chat;
    private TextView newMessage;
    private BaeconAdapter baeconAdapter;
    private String username;
    private Dialog usernameDialog;
    private boolean superSecretMode;

    private final MessageListener messageListener = new MessageListener() {

        @Override
        public void onFound(Message message) {
            // Message.MAX_CONTENT_SIZE_BYTES; // 102,400 ~ 100KB

            try {
                String messageJson = new String(message.getContent(), StandardCharsets.UTF_8);
                final Baecon baecon = new Baecon(messageJson);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        addBaecon(baecon);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Whoops", e);
            }
        }
    };

    private void addBaecon(Baecon baecon) {
        baeconAdapter.addBaecon(baecon);
        chat.smoothScrollToPosition(baeconAdapter.getItemCount() - 1);
    }

    @Override
    public void onClick(View v) {
        String message = newMessage.getText().toString();

        if (!TextUtils.isEmpty(message)) {
            Baecon baecon = new Baecon(username, message);
            sendMessage(baecon.toJson());
            addBaecon(baecon);
            newMessage.setText("");
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

        if (which == Dialog.BUTTON_POSITIVE) {
            username = ((EditText) usernameDialog.findViewById(R.id.username)).getText().toString();
        }

        if (TextUtils.isEmpty(username)) {
            username = UUID.randomUUID().toString();
        }

        superSecretMode = ((Switch) this.usernameDialog.findViewById(R.id.super_secret_mode)).isChecked();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baecon);
        baeconAdapter = new BaeconAdapter();
        newMessage = (EditText) findViewById(R.id.new_message);
        chat = (RecyclerView) findViewById(R.id.chat);
        chat.setLayoutManager(new LinearLayoutManager(this));
        chat.setAdapter(baeconAdapter);
        findViewById(R.id.send).setOnClickListener(this);
        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Nearby.MESSAGES_API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        if (TextUtils.isEmpty(username)) {
            usernameDialog = new AlertDialog.Builder(this)
                    .setView(LayoutInflater.from(this).inflate(R.layout.dialog_username, null))
                    .setPositiveButton(R.string.ok, this)
                    .setNegativeButton(R.string.cancel, this)
                    .setCancelable(false)
                    .create();
            usernameDialog.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!apiClient.isConnected()) {
            apiClient.connect();
        }
    }

    @Override
    protected void onPause() {

        if (apiClient.isConnected()) {
            Nearby.Messages.unsubscribe(apiClient, messageListener)
                    .setResultCallback(new ErrorCheckingCallback("unsubscribe()"));
        }
        apiClient.disconnect();
        super.onPause();
    }

    // This is called in response to a button tap in the Nearby permission dialog.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;

            if (resultCode == RESULT_OK) {
                Nearby.Messages
                        .subscribe(apiClient, messageListener)
                        .setResultCallback(new ErrorCheckingCallback("subscribe()"));
            } else {
                // This may mean that user had rejected to grant nearby permission.
                Log.i(TAG, "Failed to resolve error with code " + resultCode);
            }
        }
    }

    private void sendMessage(String messageStr) {
        // Message.MAX_CONTENT_SIZE_BYTES; // 102,400 ~ 100KB
        Message message = new Message(messageStr.getBytes());
        Nearby.Messages
                .publish(apiClient, message)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        Log.i(TAG, status.toString());
                    }
                });
    }

    @Override
    public void onConnected(Bundle bundle) {
        final Strategy strategy;

        if (superSecretMode) {
            strategy = new Strategy.Builder()
                    .setTtlSeconds(10)
                    .setDistanceType(Strategy.DISTANCE_TYPE_EARSHOT)
                    .build();
        } else {
            strategy = Strategy.DEFAULT;
        }
        Nearby.Messages.getPermissionStatus(apiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        Nearby.Messages
                                .subscribe(apiClient, messageListener, strategy)
                                .setResultCallback(new ErrorCheckingCallback("subscribe()"));
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    /**
     * A simple ResultCallback that logs when errors occur.
     * It also displays the Nearby opt-in dialog when necessary.
     */
    private class ErrorCheckingCallback implements ResultCallback<Status> {
        private final String method;
        private final Runnable runOnSuccess;

        private ErrorCheckingCallback(String method) {
            this(method, null);
        }

        private ErrorCheckingCallback(String method, @Nullable Runnable runOnSuccess) {
            this.method = method;
            this.runOnSuccess = runOnSuccess;
        }

        @Override
        public void onResult(@NonNull Status status) {
            if (status.isSuccess()) {
                Log.i(TAG, method + " succeeded.");
                if (runOnSuccess != null) {
                    runOnSuccess.run();
                }
            } else {
                // Currently, the only resolvable error is that the device is not opted
                // in to Nearby. Starting the resolution displays an opt-in dialog.
                if (status.hasResolution()) {

                    if (!mResolvingError) {

                        try {
                            status.startResolutionForResult(BaeconActivity.this, REQUEST_RESOLVE_ERROR);
                            mResolvingError = true;
                        } catch (IntentSender.SendIntentException e) {
                            Log.e(TAG, method + " failed with exception: " + e);
                        }
                    } else {
                        // This will be encountered on initial startup because we do
                        // both publish and subscribe together.
                        Log.i(TAG, method + " failed with status: " + status
                                + " while resolving error.");
                    }
                } else {
                    Log.e(TAG, method + " failed with : " + status
                            + " resolving error: " + mResolvingError);
                }
            }
        }
    }

    private static class BaeconAdapter extends RecyclerView.Adapter<BaeconHolder> {

        private final List<Baecon> baeconList;

        public BaeconAdapter() {
            baeconList = new ArrayList<>();
        }

        public void addBaecon(Baecon baecon) {
            baeconList.add(baecon);
            int position = baeconList.size() - 1;
            notifyItemInserted(position);
        }

        @Override
        public BaeconHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message, parent, false);
            return new BaeconHolder(view);
        }

        @Override
        public void onBindViewHolder(BaeconHolder holder, int position) {
            Baecon baecon = baeconList.get(position);
            holder.message.setText(baecon.getMessage());
            holder.sender.setText(baecon.getUsername());
        }

        @Override
        public int getItemCount() {
            return baeconList.size();
        }
    }

    private static class BaeconHolder extends RecyclerView.ViewHolder {

        TextView message;
        TextView sender;

        public BaeconHolder(View itemView) {
            super(itemView);
            message = (TextView) itemView.findViewById(R.id.message_content);
            sender = (TextView) itemView.findViewById(R.id.sender);
        }
    }
}

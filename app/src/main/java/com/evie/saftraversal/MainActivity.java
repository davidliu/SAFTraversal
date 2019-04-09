package com.evie.saftraversal;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LOL";
    private static final int REQUEST_DOCUMENT_PROVIDER_TRAVERSAL = 1;
    private static final int REQUEST_DOCUMENT_FILE_TRAVERSAL = 2;

    ContentResolver contentResolver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        contentResolver = getContentResolver();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
        }

        findViewById(R.id.test_file_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                traverseExternalStorageRoot();
            }
        });
        findViewById(R.id.test_document_file_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, REQUEST_DOCUMENT_FILE_TRAVERSAL);
            }
        });
        findViewById(R.id.test_document_provider_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, REQUEST_DOCUMENT_PROVIDER_TRAVERSAL);
            }
        });
    }

    private void traverseExternalStorageRoot() {
        long now = SystemClock.elapsedRealtime();
        File root = Environment.getExternalStorageDirectory();
        Log.d(TAG, "root file = " + root.getName());

        Queue<File> queue = new ArrayDeque<>();
        queue.add(root);

        MutableInteger fileCount = new MutableInteger(1);
        while (queue.size() > 0) {
            File curFile = queue.remove();
            traverseFileTree(curFile, queue, fileCount);
        }


        long then = SystemClock.elapsedRealtime();
        long time = then - now;
        Log.d(TAG, "file traversal done!");
        Log.d(TAG, fileCount.value + " files took " + time + "ms");
    }

    private void traverseFileTree(File curFile, Queue<File> queue, MutableInteger fileCount) {
        File[] files = curFile.listFiles();

        for (File file : files) {
            fileCount.increment();
            Log.d(TAG, "found file=" + file.getName() + ", parent=" + curFile.getPath());
            if (file.isDirectory()) {
                queue.add(file);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DOCUMENT_PROVIDER_TRAVERSAL && resultCode == RESULT_OK) {
            traverseTree(data.getData());
        } else if (requestCode == REQUEST_DOCUMENT_FILE_TRAVERSAL && resultCode == RESULT_OK) {
            traverseDocumentTree(data.getData());
        }
    }


    private void traverseTree(Uri uri) {

        long now = SystemClock.elapsedRealtime();
        String docId = DocumentsContract.getTreeDocumentId(uri);

        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                docId);

        Cursor docCursor = contentResolver.query(docUri, new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        try {
            while (docCursor.moveToNext()) {
                Log.d(TAG, "root doc =" + docCursor.getString(0) + ", mime=" + docCursor
                        .getString(1));
            }
        } finally {
            closeQuietly(docCursor);
        }

        Queue<String> queue = new ArrayDeque<>();

        queue.add(docId);

        MutableInteger fileCount = new MutableInteger(1);
        while (queue.size() > 0) {
            String currentDocId = queue.remove();
            traverseTree(uri, currentDocId, queue, fileCount);
        }

        long then = SystemClock.elapsedRealtime();
        long time = then - now;
        Log.d(TAG, "SAF tree traversal done!");
        Log.d(TAG, fileCount.value + " documents took " + time + "ms");
    }

    private void traverseTree(Uri rootUri, String documentId, Queue<String> queue, MutableInteger fileCount) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri,
                documentId);

        Cursor cursor = contentResolver.query(childrenUri, new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);
        try {
            while (cursor.moveToNext()) {
                fileCount.increment();
                String displayName = cursor.getString(0);
                String mimeType = cursor.getString(1);
                Log.d(TAG, "found child=" + displayName + ", mime=" + mimeType + ", parent=" + documentId);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    queue.add(cursor.getString(2));
                }
            }
        } finally {
            closeQuietly(cursor);
        }
    }

    private void traverseDocumentTree(Uri data) {

        long now = SystemClock.elapsedRealtime();
        DocumentFile file = DocumentFile.fromTreeUri(this, data);

        String displayName = file.getName();
        String mimeType = file.getType();
        Log.d(TAG, "root=" + displayName + ", mime=" + mimeType);

        MutableInteger fileCount = new MutableInteger(1);
        Queue<DocumentFile> queue = new ArrayDeque<>();

        queue.add(file);

        while (queue.size() > 0) {
            DocumentFile curFile = queue.remove();
            traverseDocumentTree(curFile, queue, fileCount);
        }

        long then = SystemClock.elapsedRealtime();
        long time = then - now;
        Log.d(TAG, "DocumentFile tree traversal done!");
        Log.d(TAG, fileCount.value + " documents took " + time + "ms");
    }

    private void traverseDocumentTree(DocumentFile documentFile, Queue<DocumentFile> queue, MutableInteger fileCount) {

        DocumentFile[] children = documentFile.listFiles();

        for (DocumentFile child : children) {
            fileCount.increment();
            String displayName = child.getName();
            boolean isDirectory = child.isDirectory();
            Log.d(TAG, "found child=" + displayName + ", isDir=" + isDirectory + ", parent=" + documentFile.getUri());

            if (isDirectory) {
                queue.add(child);
            }
        }
    }

    public void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    public class MutableInteger {
        private int value;

        public MutableInteger(int value) {
            this.value = value;
        }

        public void set(int value) {
            this.value = value;
        }

        public void increment() {
            this.value++;
        }

        public int intValue() {
            return value;
        }
    }
}

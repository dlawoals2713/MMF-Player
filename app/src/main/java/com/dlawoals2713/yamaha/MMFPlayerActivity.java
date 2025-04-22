package com.dlawoals2713.yamaha;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.Choreographer;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.textfield.TextInputLayout;
import com.yamaha.smafsynth.m7.emu.DataParser;
import com.yamaha.smafsynth.m7.emu.EmuSmw7;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.dlyt.yanndroid.oneui.dialog.ProgressDialog;
import de.dlyt.yanndroid.oneui.layout.ToolbarLayout;

public class MMFPlayerActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    private static final int PICK_MMF_FILE = 1;
    
    //I Hate Yamaha!!!! AHHHHHHHHHHHHHHHHHH
    private ImageButton initButton;
    private ImageButton playButton;
    private ImageButton stopButton;
    private ImageButton releaseButton;
    private TextView fileNameTextView;
    private TextView timeTextView;
    private SeekBar progressSeekBar;
    private SeekBar volumeSeekBar;
    private LinearLayout content;
    
    private TextView titleTextView;
    private TextView artistTextView;
    private TextView copyrightTextView;
    private TextView genreTextView;
    private TextView miscTextView;
    
    private EmuSmw7 emuSmw7;
    private int sampleRate = 22050;
    private Handler handler = new Handler();
    private Timer updateTimer;
    private byte[] mmfData;
    private String currentFileName = "파일이 선택되지 않음";
    private DataParser dataParser;
    
    private ToolbarLayout toolbarLayout;
    private SharedPreferences sp;
    
    private Choreographer.FrameCallback frameCallback;
    private boolean isUpdating = false;
    
    private de.dlyt.yanndroid.oneui.dialog.ProgressDialog dialog;
    private Context context;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Intent intent = new Intent();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mmf_player);
        
        ActivityCompat.requestPermissions(this, 
            new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 
            1);
	    sp = getSharedPreferences("setting", Activity.MODE_PRIVATE);
        initUI();
        emuSmw7 = new EmuSmw7();
        context = this;

        Uri fileUri = getIntent().getData();
        if (fileUri != null) {
            try {
                // 파일 이름 가져오기
                currentFileName = getFileNameFromUri(fileUri);
                fileNameTextView.setText(currentFileName);
                
                // 컨텐트 리졸버를 통해 직접 스트림 열기
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                if (inputStream != null) {
                    mmfData = new byte[inputStream.available()];
                    inputStream.read(mmfData);
                    inputStream.close();
                    
                    // 파일 로드 시 메타데이터 파싱
                    parseAndDisplayMetadata();
                    
                    Toast.makeText(this, "파일 로드됨: " + currentFileName, Toast.LENGTH_SHORT).show();
                    updateButtonStates();
                    Log.d("MMFPlayer", "파일 URI: " + fileUri.toString());
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error loading file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            Log.d("MMFPlayer", "Error: File URI is null");
        }    
    }
    
    private void initUI() {
        content = findViewById(R.id.content);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        
        sampleRate = Integer.parseInt(sp.getString("sr", "22050"));
        
        fileNameTextView = findViewById(R.id.text_file_name);
        fileNameTextView.setText(currentFileName);
        
        titleTextView = findViewById(R.id.text_title);
        artistTextView = findViewById(R.id.text_artist);
        copyrightTextView = findViewById(R.id.text_copyright);
        genreTextView = findViewById(R.id.text_genre);
        miscTextView = findViewById(R.id.text_misc);
    
        initButton = findViewById(R.id.button_init);
        playButton = findViewById(R.id.button_play);
        stopButton = findViewById(R.id.button_stop);
        releaseButton = findViewById(R.id.button_release);
        
        initButton.setOnClickListener(this);
        playButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);
        releaseButton.setOnClickListener(this);
    
        progressSeekBar = findViewById(R.id.seekbar_progress);
        progressSeekBar.setEnabled(false);
        volumeSeekBar = findViewById(R.id.seekbar_volume);
        volumeSeekBar.setMax(127);
        volumeSeekBar.setProgress(100);
        volumeSeekBar.setOnSeekBarChangeListener(this);
        
        timeTextView = findViewById(R.id.text_time);
        
        toolbarLayout = findViewById(R.id.toolbar_view);
        toolbarLayout.inflateToolbarMenu(R.menu.player);
        toolbarLayout.getToolbarMenu().findItem(R.id.settings).setTitle("설정");
        toolbarLayout.setOnToolbarMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.settings:
                    setting();
                    break;
                    
                case R.id.open:
                    open();
                    break;
            }
            return true;
        });
        updateButtonStates();
    }
    
    @Override
    public void onClick(View v) {
        int id = v.getId();
        
        if (id == R.id.button_init) {
            //progressDialogCircleOnly();
            String rateText = String.valueOf(sampleRate).trim();
            sp.edit().putString("sr", rateText).apply();
        
            try {
                sampleRate = Integer.parseInt(rateText);
                if (sampleRate < 11025) sampleRate = 11025;
                if (sampleRate > 48000) sampleRate = 48000;
        
                // 기존 작업이 있으면 취소
                /*if (!executorService.isShutdown()) {
                    executorService.shutdownNow();
                }
                executorService = Executors.newSingleThreadExecutor();
        
                executorService.execute(() -> {*/
                    try {
                        int bufferSize = AudioTrack.getMinBufferSize(sampleRate,
                                AudioFormat.CHANNEL_OUT_STEREO,
                                AudioFormat.ENCODING_PCM_16BIT);
        
                        // 초기화 전에 이전 리소스 정리
                        if (emuSmw7 != null) {
                            emuSmw7.h();
                        }
        
                        long result = emuSmw7.d(sampleRate, bufferSize, AudioAttributes.USAGE_MEDIA);
        
                        runOnUiThread(() -> {
                            if (result >= 0) {
                                emuSmw7.p(0);
                                updateButtonStates();
                                Toast.makeText(MMFPlayerActivity.this, "초기화 되었습니다", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MMFPlayerActivity.this, "초기화 실패", Toast.LENGTH_SHORT).show();
                            }
                            //dialog.dismiss();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(MMFPlayerActivity.this, "초기화 중 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        });
                    }
            // });
        
            } catch (NumberFormatException e) {
                Toast.makeText(this, "유효한 샘플 레이트를 입력하세요", Toast.LENGTH_SHORT).show();
                //dialog.dismiss();
            }
        } else if (id == R.id.button_play) {
            if (mmfData == null) {
                Toast.makeText(this, "먼저 파일을 선택하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            
            parseAndDisplayMetadata();
            emuSmw7.f(mmfData, 100L, -1L, 15L, 32, 32);
            emuSmw7.e(volumeSeekBar.getProgress());
            
            startPlaybackUpdates();
            updateButtonStates();
            
        } else if (id == R.id.button_stop) {
            emuSmw7.g();
            stopPlaybackUpdates();
            updateButtonStates();
            
        } else if (id == R.id.button_release) {
            emuSmw7.h();
            stopPlaybackUpdates();
            updateButtonStates();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PICK_MMF_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        mmfData = new byte[inputStream.available()];
                        inputStream.read(mmfData);
                        inputStream.close();
                        
                        // 파일 이름 표시
                        currentFileName = getFileName(uri);
                        fileNameTextView.setText(currentFileName);
                        
                        // 파일 로드 시 메타데이터 파싱
                        parseAndDisplayMetadata();
                        
                        Toast.makeText(this, "파일 로드됨: " + currentFileName, Toast.LENGTH_SHORT).show();
                        updateButtonStates();
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "파일을 찾을 수 없음", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "파일 읽는 중 오류 발생", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    private void parseAndDisplayMetadata() {
        if (mmfData != null) {
            try {
                dataParser = new DataParser(mmfData);
                updateMetadataDisplay();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "메타데이터 파싱 오류", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void updateMetadataDisplay() {
        if (dataParser != null) {
            titleTextView.setText("제목: " + (dataParser.getTitle().isEmpty() ? "알 수 없음" : dataParser.getTitle()));
            artistTextView.setText("아티스트: " + (dataParser.getArtistName().isEmpty() ? "알 수 없음" : dataParser.getArtistName()));
            copyrightTextView.setText("저작권: " + (dataParser.getCopyrightInfo().isEmpty() ? "알 수 없음" : dataParser.getCopyrightInfo()));
            genreTextView.setText("장르: " + (dataParser.getGenre().isEmpty() ? "알 수 없음" : dataParser.getGenre()));
            miscTextView.setText("기타 정보: " + (dataParser.getMiscInfo().isEmpty() ? "알 수 없음" : dataParser.getMiscInfo()));
        } else {
            titleTextView.setText("제목: 알 수 없음");
            artistTextView.setText("아티스트: 알 수 없음");
            copyrightTextView.setText("저작권: 알 수 없음");
            genreTextView.setText("장르: 알 수 없음");
            miscTextView.setText("기타 정보: 알 수 없음");
        }
    }
    
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    
    private void updateButtonStates() {
        if (emuSmw7 == null) {
            int cardBackground = getResources().getColor(R.color.cardBackground);
            int primaryColor = getResources().getColor(R.color.primaryColor);
            resetButtons(cardBackground);
            initButton.setEnabled(true);
            initButton.setImageTintList(ColorStateList.valueOf(primaryColor));
            return;
        }
        
        long state = emuSmw7.c();
        
        int primaryColor = getResources().getColor(R.color.primaryColor);
        int cardBackground = getResources().getColor(R.color.cardBackground);
        int errorColor = getResources().getColor(R.color.errorColor);
        
        resetButtons(cardBackground);
        
        switch ((int) state) {
            case 0:
                initButton.setEnabled(true);
                initButton.setImageTintList(ColorStateList.valueOf(primaryColor));
                break;
            case 1:
                if (mmfData != null) {
                    playButton.setEnabled(true);
                    playButton.setImageTintList(ColorStateList.valueOf(primaryColor));
                }
                releaseButton.setEnabled(true);
                releaseButton.setImageTintList(ColorStateList.valueOf(errorColor));
                break;
            case 2:
                stopButton.setEnabled(true);
                stopButton.setImageTintList(ColorStateList.valueOf(primaryColor));
                releaseButton.setEnabled(true);
                releaseButton.setImageTintList(ColorStateList.valueOf(errorColor));
                break;
        }
    }
    
    // 모든 버튼을 기본 상태로 리셋
    private void resetButtons(int defaultColor) {
        ColorStateList defaultTint = ColorStateList.valueOf(defaultColor);
        
        initButton.setEnabled(false);
        initButton.setImageTintList(defaultTint);
        
        playButton.setEnabled(false);
        playButton.setImageTintList(defaultTint);
        
        stopButton.setEnabled(false);
        stopButton.setImageTintList(defaultTint);
        
        releaseButton.setEnabled(false);
        releaseButton.setImageTintList(defaultTint);
    }
    
    private void startPlaybackUpdates() {
        stopPlaybackUpdates();
        isUpdating = true;
        
        frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (emuSmw7 != null && isUpdating) {
                    long position = emuSmw7.b();
                    long length = emuSmw7.a();
                    
                    if (length > 0) {
                        progressSeekBar.setMax((int) length);
                        progressSeekBar.setProgress((int) position);
                        
                        timeTextView.setText(
                                formatTime(position) + " / " + formatTime(length));
                    }
                }
                
                if (isUpdating) {
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        };
        
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }
    
    private void stopPlaybackUpdates() {
        isUpdating = false;
        if (frameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            frameCallback = null;
        }
    }
    
    private String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == volumeSeekBar && emuSmw7 != null) {
            emuSmw7.e(progress);
        }
    }
    
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}
    
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emuSmw7 != null) {
            emuSmw7.g();
            emuSmw7.h();
            emuSmw7 = null;
        }
        stopPlaybackUpdates();
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
    
    private void setting() {
        intent.setClass(getApplicationContext(), SettingActivity.class);
        startActivity(intent);
    }
    
    private void open() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_MMF_FILE);
    }
    
    private void progressDialogCircleOnly() {
        dialog = new de.dlyt.yanndroid.oneui.dialog.ProgressDialog(context);
        dialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE_ONLY);
        dialog.setCancelable(false);
	    dialog.show();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        sampleRate = Integer.parseInt(sp.getString("sr", "22050"));
    }
    
    private String getFileNameFromUri(Uri uri) {
        String displayName = "";
        try (Cursor cursor = getContentResolver().query(
                uri, 
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, 
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 대체 방법: URI에서 직접 이름 추출
            displayName = uri.getLastPathSegment();
        }
        return displayName;
    }
}


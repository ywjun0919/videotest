package ycj.com.vediotest.videoeditor;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.liteav.basic.log.TXCLog;
//import com.tencent.liteav.demo.R;
//import com.tencent.liteav.demo.common.activity.videopreview.TCVideoPreviewActivity;
//import com.tencent.liteav.demo.common.utils.TCConstants;
//import com.tencent.liteav.demo.common.widget.VideoWorkProgressFragment;
//import com.tencent.liteav.demo.shortvideo.choose.TCVideoFileInfo;
//import com.tencent.liteav.demo.shortvideo.editor.bgm.TCBGMInfo;
//import com.tencent.liteav.demo.shortvideo.editor.word.TCWordEditorFragment;
import com.tencent.ugc.TXVideoEditConstants;
import com.tencent.ugc.TXVideoEditer;
import com.tencent.ugc.TXVideoInfoReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

import ycj.com.vediotest.R;
import ycj.com.vediotest.VedioRecorderActivity;
import ycj.com.vediotest.videoeditor.choose.TCVideoChooseActivity;
import ycj.com.vediotest.videoeditor.choose.TCVideoFileInfo;
import ycj.com.vediotest.videoeditor.widget.VideoWorkProgressFragment;

/**
 * UGC短视频裁剪
 */
public class TCVideoEditerActivity extends FragmentActivity implements View.OnClickListener,
        TXVideoEditer.TXVideoGenerateListener, TXVideoInfoReader.OnSampleProgrocess, TXVideoEditer.TXVideoPreviewListener, Edit.OnCutChangeListener {

    private static final String TAG = TCVideoEditerActivity.class.getSimpleName();
    private final int MSG_LOAD_VIDEO_INFO = 1000;
    private final int MSG_RET_VIDEO_INFO = 1001;

    private int mCurrentState = PlayState.STATE_NONE;

    private TextView mTvDone;
    private TextView mTvCurrent;
    private TextView mTvDuration;
    private ImageButton mBtnPlay;
    private FrameLayout mVideoView;
    private LinearLayout mLayoutEditer;
    private EditPannel mEditPannel;
    private ProgressBar mLoadProgress;
    private VideoWorkProgressFragment mWorkProgressDialog;
//    private TCWordEditorFragment mTCWordEditorFragment;     // 添加字幕的Fragment
    /**************************SDK*****************************/
    private TXVideoEditer mTXVideoEditer;
    private TCVideoFileInfo mTCVideoFileInfo;
    private TXVideoInfoReader mTXVideoInfoReader;

    private TXVideoEditConstants.TXVideoInfo mTXVideoInfo;
    private TXVideoEditConstants.TXGenerateResult mResult;
    private String mBGMPath;                                // BGM路径
    private String mVideoOutputPath;
    private float mSpeedLevel = 1.0f;                       // 加速速度
    private BackGroundHandler mHandler;
    private int mCutVideoDuration;//裁剪的视频时长

    private Bitmap mWaterMarkLogo;
    private boolean mIsStopManually;//标记是否手动停止

    private HandlerThread mBGHandlerThread;

    class BackGroundHandler extends Handler {

        public BackGroundHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LOAD_VIDEO_INFO:
                    TXVideoEditConstants.TXVideoInfo videoInfo = mTXVideoInfoReader.getVideoFileInfo(mTCVideoFileInfo.getFilePath());
                    if (videoInfo == null) {
                        mLoadProgress.setVisibility(View.GONE);

                        showUnSupportDialog("暂不支持Android 4.3以下的系统");
                        return;
                    }
                    Message mainMsg = new Message();
                    mainMsg.what = MSG_RET_VIDEO_INFO;
                    mainMsg.obj = videoInfo;
                    mMainHandler.sendMessage(mainMsg);
                    break;
            }

        }
    }

    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RET_VIDEO_INFO:
                    mTXVideoInfo = (TXVideoEditConstants.TXVideoInfo) msg.obj;

                    TXVideoEditConstants.TXPreviewParam param = new TXVideoEditConstants.TXPreviewParam();
                    param.videoView = mVideoView;
                    param.renderMode = TXVideoEditConstants.PREVIEW_RENDER_MODE_FILL_EDGE;
                    int ret = mTXVideoEditer.setVideoPath(mTCVideoFileInfo.getFilePath());
                    mTXVideoEditer.initWithPreview(param);
                    if (ret < 0) {
                        showUnSupportDialog("本机型暂不支持此视频格式");
                        return;
                    }

                    handleOp(Action.DO_SEEK_VIDEO, 0, (int) mTXVideoInfo.duration);
                    mLoadProgress.setVisibility(View.GONE);
                    mEditPannel.setOnClickable(true);
                    mTvDone.setClickable(true);
                    mBtnPlay.setClickable(true);

                    mEditPannel.setMediaFileInfo(mTXVideoInfo);
                    String duration = TCUtils.duration(mTXVideoInfo.duration);
                    String position = TCUtils.duration(0);

                    mTvCurrent.setText(position);
                    mTvDuration.setText(duration);
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.activity_video_editer);

        initViews();
        initData();
    }

    @Override
    protected void onDestroy() {
        TelephonyManager tm = (TelephonyManager) this.getApplicationContext().getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);

        mBGHandlerThread.quit();
        handleOp(Action.DO_CANCEL_VIDEO, 0, 0);

        mTXVideoInfoReader.cancel();
        mTXVideoEditer.setTXVideoPreviewListener(null);
        mTXVideoEditer.setVideoGenerateListener(null);
        super.onDestroy();
    }

    private void initViews() {
        mEditPannel = (EditPannel) findViewById(R.id.edit_pannel);
        mEditPannel.setCutChangeListener(this);
//        mEditPannel.setFilterChangeListener(this);
//        mEditPannel.setBGMChangeListener(this);
//        mEditPannel.setWordChangeListener(this);
//        mEditPannel.setSpeedChangeListener(this);
        mEditPannel.setOnClickable(false);

        mTvCurrent = (TextView) findViewById(R.id.tv_current);
        mTvDuration = (TextView) findViewById(R.id.tv_duration);

        mVideoView = (FrameLayout) findViewById(R.id.video_view);

        mBtnPlay = (ImageButton) findViewById(R.id.btn_play);
        mBtnPlay.setOnClickListener(this);
        mBtnPlay.setClickable(false);

        LinearLayout backLL = (LinearLayout) findViewById(R.id.back_ll);
        backLL.setOnClickListener(this);

        mTvDone = (TextView) findViewById(R.id.btn_done);
        mTvDone.setOnClickListener(this);
        mTvDone.setClickable(false);

        mLayoutEditer = (LinearLayout) findViewById(R.id.layout_editer);
        mLayoutEditer.setEnabled(true);

        mLoadProgress = (ProgressBar) findViewById(R.id.progress_load);
        initWorkProgressPopWin();
    }

    private void initWorkProgressPopWin() {
        if (mWorkProgressDialog == null) {
            mWorkProgressDialog = new VideoWorkProgressFragment();
            mWorkProgressDialog.setOnClickStopListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mTvDone.setClickable(true);
                    mTvDone.setEnabled(true);
                    mWorkProgressDialog.dismiss();
                    Toast.makeText(TCVideoEditerActivity.this, "取消视频生成", Toast.LENGTH_SHORT).show();
                    mWorkProgressDialog.setProgress(0);
                    mCurrentState = PlayState.STATE_NONE;
                    if (mTXVideoEditer != null) {
                        mTXVideoEditer.cancel();
                    }
                }
            });
        }
        mWorkProgressDialog.setProgress(0);
    }

    private synchronized boolean handleOp(int state, int startPlayTime, int endPlayTime) {
        switch (state) {
            case Action.DO_PLAY_VIDEO:
                if (mCurrentState == PlayState.STATE_NONE) {
                    mTXVideoEditer.startPlayFromTime(startPlayTime, endPlayTime);
                    mCurrentState = PlayState.STATE_PLAY;
                    return true;
                } else if (mCurrentState == PlayState.STATE_PAUSE) {
                    mTXVideoEditer.resumePlay();
                    mCurrentState = PlayState.STATE_PLAY;
                    return true;
                }
                break;
            case Action.DO_PAUSE_VIDEO:
                if (mCurrentState == PlayState.STATE_PLAY) {
                    mTXVideoEditer.pausePlay();
                    mCurrentState = PlayState.STATE_PAUSE;
                    return true;
                }
                break;
            case Action.DO_SEEK_VIDEO:
                if (mCurrentState == PlayState.STATE_CUT) {
                    return false;
                }
                if (mCurrentState == PlayState.STATE_PLAY || mCurrentState == PlayState.STATE_PAUSE) {
                    mTXVideoEditer.stopPlay();
                }
                mTXVideoEditer.startPlayFromTime(startPlayTime, endPlayTime);
                mCurrentState = PlayState.STATE_PLAY;
                return true;
            case Action.DO_CUT_VIDEO:
                if (mCurrentState == PlayState.STATE_PLAY || mCurrentState == PlayState.STATE_PAUSE) {
                    mTXVideoEditer.stopPlay();
                }
                startTranscode();
                mCurrentState = PlayState.STATE_CUT;
                return true;
            case Action.DO_CANCEL_VIDEO:
                if (mCurrentState == PlayState.STATE_PLAY || mCurrentState == PlayState.STATE_PAUSE) {
                    mTXVideoEditer.stopPlay();
                } else if (mCurrentState == PlayState.STATE_CUT) {
                    mTXVideoEditer.cancel();
                }
                mCurrentState = PlayState.STATE_NONE;
                return true;
        }
        return false;
    }

    private void initData() {
        //初始化后台Thread线程
        mBGHandlerThread = new HandlerThread("LoadData");
        mBGHandlerThread.start();
        mHandler = new BackGroundHandler(mBGHandlerThread.getLooper());

        mTCVideoFileInfo = (TCVideoFileInfo) getIntent().getSerializableExtra(TCConstants.INTENT_KEY_SINGLE_CHOOSE);
        mTXVideoInfoReader = TXVideoInfoReader.getInstance();

        //初始化SDK编辑
        mTXVideoEditer = new TXVideoEditer(this);
        mTXVideoEditer.setTXVideoPreviewListener(this);

        //加载视频基本信息
        mHandler.sendEmptyMessage(MSG_LOAD_VIDEO_INFO);

        //设置电话监听
        mPhoneListener = new TXPhoneStateListener(this);
        TelephonyManager tm = (TelephonyManager) this.getApplicationContext().getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);

        //加载缩略图
        mTXVideoInfoReader.getSampleImages(TCConstants.THUMB_COUNT, mTCVideoFileInfo.getFilePath(), this);

        //导入水印
        //mWaterMarkLogo = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
    }

    private void createThumbFile() {
        AsyncTask<Void, String, String> task = new AsyncTask<Void, String, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                File outputVideo = new File(mVideoOutputPath);
                if (outputVideo == null || !outputVideo.exists())
                    return null;
                Bitmap bitmap = mTXVideoInfoReader.getSampleImage(0, mVideoOutputPath);
                if (bitmap == null)
                    return null;
                String mediaFileName = outputVideo.getAbsolutePath();
                if (mediaFileName.lastIndexOf(".") != -1) {
                    mediaFileName = mediaFileName.substring(0, mediaFileName.lastIndexOf("."));
                }
                String folder = Environment.getExternalStorageDirectory() + File.separator + TCConstants.DEFAULT_MEDIA_PACK_FOLDER + File.separator + mediaFileName;
                File appDir = new File(folder);
                if (!appDir.exists()) {
                    appDir.mkdirs();
                }

                String fileName = "thumbnail" + ".jpg";
                File file = new File(appDir, fileName);
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.flush();
                    fos.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (mTCVideoFileInfo.getThumbPath() == null) {
                    mTCVideoFileInfo.setThumbPath(file.getAbsolutePath());
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                startPreviewActivity(mResult);
            }
        };
        task.execute();
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume: ");
//        if (mTCWordEditorFragment == null || mTCWordEditorFragment.isHidden()) {
//            if (mCurrentState == PlayState.STATE_PAUSE && !mIsStopManually) {
//                handleOp(Action.DO_PLAY_VIDEO, mEditPannel.getSegmentFrom(), mEditPannel.getSegmentTo());
//                mBtnPlay.setImageResource(mCurrentState == PlayState.STATE_PLAY ? R.drawable.ic_pause : R.drawable.ic_play);
//            }
//        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart: ");
//        if (mCurrentState == PlayState.STATE_NONE || mTCWordEditorFragment != null) {//说明是取消合成之后
//            handleOp(Action.DO_SEEK_VIDEO, 0, (int) mTXVideoInfo.duration);
//            mBtnPlay.setImageResource(R.drawable.ic_pause);
//        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart: ");
    }


    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop: ");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: ");
        if (mCurrentState == PlayState.STATE_CUT) {
            handleOp(Action.DO_CANCEL_VIDEO, 0, 0);
            if (mWorkProgressDialog != null && mWorkProgressDialog.isAdded()) {
                mWorkProgressDialog.dismiss();
            }
        } else {
            mIsStopManually = false;
            handleOp(Action.DO_PAUSE_VIDEO, 0, 0);
            mBtnPlay.setImageResource(mCurrentState == PlayState.STATE_PLAY ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        mTvDone.setClickable(true);
        mTvDone.setEnabled(true);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_done:
                doTranscode();
                //mTXVideoEditer.quickGenerateVideo("/sdcard/7.mp4");
                break;
            case R.id.back_ll:
                mTXVideoInfoReader.cancel();
                handleOp(Action.DO_CANCEL_VIDEO, 0, 0);
                mTXVideoEditer.setTXVideoPreviewListener(null);
                mTXVideoEditer.setVideoGenerateListener(null);
                finish();
                break;
            case R.id.btn_play:
                mIsStopManually = !mIsStopManually;
                playVideo();
                break;
        }
    }

    private void playVideo() {
        if (mCurrentState == PlayState.STATE_PLAY) {
            handleOp(Action.DO_PAUSE_VIDEO, 0, 0);
        } else {
            handleOp(Action.DO_PLAY_VIDEO, mEditPannel.getSegmentFrom(), mEditPannel.getSegmentTo());
        }
        mBtnPlay.setImageResource(mCurrentState == PlayState.STATE_PLAY ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    /**
     * 开始裁剪：
     * 经过相应的合法状态判定后，将会执行{@link TCVideoEditerActivity#startTranscode()}
     * 开始裁剪
     */
    private void doTranscode() {
        mTvDone.setEnabled(false);
        mTvDone.setClickable(false);

        mTXVideoInfoReader.cancel();
        mLayoutEditer.setEnabled(false);
        handleOp(Action.DO_CUT_VIDEO, 0, 0);
    }

    /**
     * 开始裁剪的具体执行方法
     */
    private void startTranscode() {
        mBtnPlay.setImageResource(R.drawable.ic_play);
        mCutVideoDuration = mEditPannel.getSegmentTo() - mEditPannel.getSegmentFrom();
        mWorkProgressDialog.setProgress(0);
        mWorkProgressDialog.setCancelable(false);
        mWorkProgressDialog.show(getFragmentManager(), "progress_dialog");
        try {
            mTXVideoEditer.setCutFromTime(mEditPannel.getSegmentFrom(), mEditPannel.getSegmentTo());

            String outputPath = Environment.getExternalStorageDirectory() + File.separator + TCConstants.DEFAULT_MEDIA_PACK_FOLDER;
            File outputFolder = new File(outputPath);

            if (!outputFolder.exists()) {
                outputFolder.mkdirs();
            }
            String current = String.valueOf(System.currentTimeMillis() / 1000);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String time = sdf.format(new Date(Long.valueOf(current + "000")));
            String saveFileName = String.format("TXVideo_%s.mp4", time);
            mVideoOutputPath = outputFolder + "/" + saveFileName;
            mTXVideoEditer.setVideoGenerateListener(this);
            mTXVideoEditer.generateVideo(TXVideoEditConstants.VIDEO_COMPRESSED_540P, mVideoOutputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 错误框方法
     */
    private void showUnSupportDialog(String text) {
        AlertDialog.Builder normalDialog = new AlertDialog.Builder(TCVideoEditerActivity.this, R.style.ConfirmDialogStyle);
        normalDialog.setMessage(text);
        normalDialog.setCancelable(false);
        normalDialog.setPositiveButton("我知道了", null);
        normalDialog.show();
    }

    /********************************************* SDK回调**************************************************/
    @Override
    public void onGenerateProgress(final float progress) {
        final int prog = (int) (progress * 100);
        mWorkProgressDialog.setProgress(prog);
    }

    @Override
    public void onGenerateComplete(TXVideoEditConstants.TXGenerateResult result) {
        if (result.retCode == TXVideoEditConstants.GENERATE_RESULT_OK) {
            if (mTXVideoInfo != null) {
                mResult = result;
                createThumbFile();
            }
            finish();
        } else {
            final TXVideoEditConstants.TXGenerateResult ret = result;
            Toast.makeText(TCVideoEditerActivity.this, ret.descMsg, Toast.LENGTH_SHORT).show();
            mTvDone.setEnabled(true);
            mTvDone.setClickable(true);
        }
        mCurrentState = PlayState.STATE_NONE;
    }

    private void startPreviewActivity(TXVideoEditConstants.TXGenerateResult result) {
        TXCLog.d(TAG,"startPreviewActivity");

        TXCLog.d(TAG,String.format("TCConstants.VIDEO_RECORD_RESULT:%s",result.retCode));
        TXCLog.d(TAG,String.format("TCConstants.VIDEO_RECORD_DESCMSG:%s",result.descMsg));
        TXCLog.d(TAG,String.format("TCConstants.VIDEO_RECORD_VIDEPATH:%s",mVideoOutputPath));
        TXCLog.d(TAG,String.format("TCConstants.VIDEO_RECORD_COVERPATH:%s",mTCVideoFileInfo.getThumbPath()));
        TXCLog.d(TAG,String.format("TCConstants.VIDEO_RECORD_DURATION:%s",mCutVideoDuration));

//        Intent intent = new Intent(getApplicationContext(), TCVideoPreviewActivity.class);
//        intent.putExtra(TCConstants.VIDEO_RECORD_TYPE, TCConstants.VIDEO_RECORD_TYPE_PLAY);
//        intent.putExtra(TCConstants.VIDEO_RECORD_RESULT, result.retCode);
//        intent.putExtra(TCConstants.VIDEO_RECORD_DESCMSG, result.descMsg);
//        intent.putExtra(TCConstants.VIDEO_RECORD_VIDEPATH, mVideoOutputPath);
//        intent.putExtra(TCConstants.VIDEO_RECORD_COVERPATH, mTCVideoFileInfo.getThumbPath());
//        intent.putExtra(TCConstants.VIDEO_RECORD_DURATION, mCutVideoDuration);
        Intent intent = new Intent(getApplicationContext(), VedioRecorderActivity.class);
        startActivity(intent);
    }

    @Override
    public void sampleProcess(int number, Bitmap bitmap) {
        int num = number;
        Bitmap bmp = bitmap;
        mEditPannel.addBitmap(num, bmp);
        TXCLog.d(TAG, "number = " + number + ",bmp = " + bitmap);
    }


    @Override
    public void onPreviewProgress(final int time) {
        if (mTvCurrent != null) {
            mTvCurrent.setText(TCUtils.duration((long) (time / 1000 * mSpeedLevel)));
        }
    }

    @Override
    public void onPreviewFinished() {
        TXCLog.d(TAG, "---------------onPreviewFinished-----------------");
        handleOp(Action.DO_SEEK_VIDEO, mEditPannel.getSegmentFrom(), mEditPannel.getSegmentTo());
    }

    /********************************************* 裁剪**************************************************/
    @Override
    public void onCutChangeKeyDown() {
        mBtnPlay.setImageResource(R.drawable.ic_play);
    }

    @Override
    public void onCutChangeKeyUp(int startTime, int endTime) {
        mBtnPlay.setImageResource(R.drawable.ic_pause);
        handleOp(Action.DO_SEEK_VIDEO, mEditPannel.getSegmentFrom(), mEditPannel.getSegmentTo());
    }

    /********************************************* 加速**************************************************/
//    @Override //开启加速的回调
//    public void onSpeedChange(float speed) {
//        mSpeedLevel = speed;
//        mTXVideoEditer.setSpeedLevel(mSpeedLevel);
//    }
//
//    /********************************************* 滤镜**************************************************/
//    @Override //选择了具体滤镜的回调
//    public void onFilterChange(Bitmap bitmap) {
//        mTXVideoEditer.setFilter(bitmap);
//    }
//
//    /********************************************* 背景音**************************************************/
//    @Override //BGM的音量的改变回调
//    public void onBGMSeekChange(float progress) {
//        mTXVideoEditer.setBGMVolume(mEditPannel.getBGMVolumeProgress());
//        mTXVideoEditer.setVideoVolume(1 - mEditPannel.getBGMVolumeProgress());
//    }

//    @Override //移除BGM回调
//    public void onBGMDelete() {
//        mTXVideoEditer.setBGM(null);
//    }
//
//    @Override //选中BGM的回调
//    public boolean onBGMInfoSetting(TCBGMInfo info) {
//        mTXVideoEditer.setBGMVolume(mEditPannel.getBGMVolumeProgress());
//        mTXVideoEditer.setVideoVolume(1 - mEditPannel.getBGMVolumeProgress());
//        mBGMPath = info.getPath();
//        if (!TextUtils.isEmpty(mBGMPath)) {
//            int result = mTXVideoEditer.setBGM(mBGMPath);
//            if (result != 0) {
//                showUnSupportDialog("背景音仅支持MP3格式音频");
//            }
//            return result == 0;//设置成功
//        }
//        return false;
//    }
//
//    @Override //开始滑动BGM区间的回调
//    public void onBGMRangeKeyDown() {
//
//    }
//
//    @Override //BGM起止时间的回调
//    public void onBGMRangeKeyUp(long startTime, long endTime) {
//        if (!TextUtils.isEmpty(mBGMPath)) {
//            mTXVideoEditer.setBGMStartTime(startTime, endTime);
//        }
//    }
//
//    /********************************************* 字幕**************************************************/
//    @Override //点击添加字幕的回调
//    public void onWordClick() {
//        if (mTCWordEditorFragment == null) {
//            mTCWordEditorFragment = TCWordEditorFragment.newInstance(mTXVideoEditer,
//                    mEditPannel.getSegmentFrom(), mEditPannel.getSegmentTo());
//            mTCWordEditorFragment.setOnWordEditorListener(TCVideoEditerActivity.this);
//            mTCWordEditorFragment.setSpeedLevel(mSpeedLevel);
//            getSupportFragmentManager()
//                    .beginTransaction()
//                    .replace(R.id.editer_fl_word_container, mTCWordEditorFragment, "editor_word_fragment")
//                    .commit();
//        } else {
//            mTCWordEditorFragment.setVideoRangeTime(mEditPannel.getSegmentFrom(), mEditPannel.getSegmentTo());
//            mTCWordEditorFragment.setSpeedLevel(mSpeedLevel);
//            getSupportFragmentManager()
//                    .beginTransaction()
//                    .show(mTCWordEditorFragment)
//                    .commit();
//        }
//    }
//
//
//    /********************************************* 字幕Fragment回调**************************************************/
//
//    @Override //从字幕的Fragment取消回来的回调
//    public void onWordEditCancel() {
//        removeWordEditorFragment();
//        resetAndPlay();
//    }
//
//
//    @Override //从字幕的Fragment点击保存回来的hi掉
//    public void onWordEditFinish() {
//        removeWordEditorFragment();
//        resetAndPlay();
//    }
//
//
//    private void removeWordEditorFragment() {
//        if (mTCWordEditorFragment != null && mTCWordEditorFragment.isAdded()) {
//            getSupportFragmentManager().beginTransaction().hide(mTCWordEditorFragment).commit();
//        }
//    }
//
//    /**
//     * 从字幕编辑回来之后，要重新设置Video的容器，以及监听进度回调
//     */
//    private void resetAndPlay() {
//        mBtnPlay.setImageResource(R.drawable.ic_pause);
//        mCurrentState = PlayState.STATE_PLAY;
//        TXVideoEditConstants.TXPreviewParam param = new TXVideoEditConstants.TXPreviewParam();
//        param.videoView = mVideoView;
//        param.renderMode = TXVideoEditConstants.PREVIEW_RENDER_MODE_FILL_EDGE;
//        mTXVideoEditer.initWithPreview(param);
//        mTXVideoEditer.startPlayFromTime(mEditPannel.getSegmentFrom(), mEditPannel.getSegmentTo());
//        mTXVideoEditer.setTXVideoPreviewListener(this);
//    }


    /*********************************************监听电话状态**************************************************/
    static class TXPhoneStateListener extends PhoneStateListener {
        WeakReference<TCVideoEditerActivity> mJoiner;

        public TXPhoneStateListener(TCVideoEditerActivity joiner) {
            mJoiner = new WeakReference<TCVideoEditerActivity>(joiner);
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            TCVideoEditerActivity joiner = mJoiner.get();
            if (joiner == null) return;
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:  //电话等待接听
                case TelephonyManager.CALL_STATE_OFFHOOK:  //电话接听
                    if (joiner.mCurrentState == PlayState.STATE_CUT) {
                        joiner.handleOp(Action.DO_CANCEL_VIDEO, 0, 0);
                        if (joiner.mWorkProgressDialog != null && joiner.mWorkProgressDialog.isAdded()) {
                            joiner.mWorkProgressDialog.dismiss();
                        }
                        joiner.mBtnPlay.setImageResource(R.drawable.ic_pause);
                    } else {
                        joiner.handleOp(Action.DO_PAUSE_VIDEO, 0, 0);
                        if (joiner.mBtnPlay != null) {
                            joiner.mBtnPlay.setImageResource(joiner.mCurrentState == PlayState.STATE_PLAY ? R.drawable.ic_pause : R.drawable.ic_play);
                        }
                    }
                    if (joiner.mTvDone != null) {
                        joiner.mTvDone.setClickable(true);
                        joiner.mTvDone.setEnabled(true);
                    }
                    break;
                //电话挂机
                case TelephonyManager.CALL_STATE_IDLE:
                    joiner.mBtnPlay.setImageResource(R.drawable.ic_pause);
                    if (joiner.mTXVideoEditer != null && joiner.mEditPannel != null)
                        joiner.handleOp(Action.DO_PLAY_VIDEO, joiner.mEditPannel.getSegmentFrom(), joiner.mEditPannel.getSegmentTo());
                    break;
            }
        }
    }

    ;
    private PhoneStateListener mPhoneListener = null;

}

package com.ray.balloon.view.video;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.corelibs.base.BaseActivity;
import com.corelibs.base.BaseRxPresenter;
import com.danikula.videocache.CacheListener;
import com.danikula.videocache.HttpProxyCacheServer;
import com.pili.pldroid.player.AVOptions;
import com.pili.pldroid.player.PlayerCode;
import com.pili.pldroid.player.common.Util;
import com.pili.pldroid.player.widget.VideoView;
import com.ray.balloon.App;
import com.ray.balloon.R;
import com.ray.balloon.widget.player.AspectLayout;
import com.ray.balloon.widget.player.MediaController;

import java.io.File;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by Ray on 2016/3/19.
 * https://github.com/ray0807
 */
public class VideoPlayerActivity extends BaseActivity implements CacheListener,
        IjkMediaPlayer.OnCompletionListener,
        IjkMediaPlayer.OnInfoListener,
        IjkMediaPlayer.OnErrorListener,
        IjkMediaPlayer.OnVideoSizeChangedListener,
        IjkMediaPlayer.OnPreparedListener {
    private static final int REQ_DELAY_MILLS = 3000;

    private static final String TAG = "VideoPlayerActivity";
    private ViewGroup.LayoutParams mLayoutParams;
    private String mVideoPath;
    private AspectLayout mAspectLayout;
    private VideoView mVideoView;
    private View mBufferingIndicator;
    private MediaController mMediaController;
    private boolean mIsLiveStream = false;

    private int mReqDelayMills = REQ_DELAY_MILLS;
    private boolean mIsCompleted = false;
    private Runnable mVideoReconnect;
    private long mLastPosition = 0;
    private Pair<Integer, Integer> mScreenSize;
    private HttpProxyCacheServer proxy;

    private RelativeLayout rl_play_finished;
    private TextView tv_finish_tag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    protected int getLayoutId() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        return R.layout.activity_player;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        mVideoPath = getIntent().getStringExtra("videoPath");
        Intent intent = getIntent();
        String intentAction = intent.getAction();
        if (!TextUtils.isEmpty(intentAction) && intentAction.equals(Intent.ACTION_VIEW)) {
            mVideoPath = intent.getDataString();
        }

        mAspectLayout = (AspectLayout) findViewById(R.id.aspect_layout);

        proxy = App.getProxy(this);
        proxy.registerCacheListener(this, mVideoPath);

        findViews();
        init();
        addListener();
    }


    @Override
    protected BaseRxPresenter createPresenter() {
        return null;
    }

    public void findViews() {
        mBufferingIndicator = findViewById(R.id.buffering_indicator);
        rl_play_finished = (RelativeLayout) findViewById(R.id.rl_play_finished);
        tv_finish_tag = (TextView) findViewById(R.id.tv_finish_tag);
        boolean useFastForward = true;
        boolean disableProgressBar = false;

        Log.i(TAG, "mVideoPath:" + mVideoPath);
        // Tip: you can custom the variable depending on your situation
        mIsLiveStream = false;//直播才设置  true:  不让快进
        if (mIsLiveStream) {
            disableProgressBar = true;
            useFastForward = false;
        }
        mMediaController = new MediaController(this, useFastForward, disableProgressBar);
    }

    public void init() {
        mVideoView = (VideoView) findViewById(R.id.video_view);
        //mVideoView.setVideoPath(mVideoPath);
        //mVideoView.start();
        mMediaController.setMediaPlayer(mVideoView);
        mVideoView.setMediaController(mMediaController);
        mVideoView.setMediaBufferingIndicator(mBufferingIndicator);

        AVOptions options = new AVOptions();
        options.setInteger(AVOptions.KEY_MEDIACODEC, 0); // 1 -> enable, 0 -> disable

        Log.i(TAG, "mIsLiveStream:" + mIsLiveStream);
        if (mIsLiveStream) {
            options.setInteger(AVOptions.KEY_BUFFER_TIME, 1000); // the unit of buffer time is ms
            options.setInteger(AVOptions.KEY_GET_AV_FRAME_TIMEOUT, 10 * 1000); // the unit of timeout is ms
            options.setString(AVOptions.KEY_FFLAGS, AVOptions.VALUE_FFLAGS_NOBUFFER); // "nobuffer"
            options.setInteger(AVOptions.KEY_LIVE_STREAMING, 1);
        }

        mVideoView.setAVOptions(options);

        mVideoView.setVideoPath(proxy.getProxyUrl(mVideoPath));

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setOnInfoListener(this);
        mVideoView.setOnPreparedListener(this);
        mVideoView.setOnVideoSizeChangedListener(this);

        mVideoView.requestFocus();
        //mVideoView.start();
        mBufferingIndicator.setVisibility(View.VISIBLE);

    }

    private void addListener() {
        //播放完点击退出
        rl_play_finished.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tv_finish_tag.setText("正在退出...");
                finish();
            }
        });
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        Log.d(TAG, "onCompletion");
        rl_play_finished.setVisibility(View.VISIBLE);
        mIsCompleted = true;
        //重复播放
        //mBufferingIndicator.setVisibility(View.GONE);
        //mVideoView.start();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        Log.d(TAG, "onError what=" + what + ", extra=" + extra);
        if (what == -10000) {
            if (extra == PlayerCode.EXTRA_CODE_INVALID_URI || extra == PlayerCode.EXTRA_CODE_EOF) {
                if (mBufferingIndicator != null)
                    mBufferingIndicator.setVisibility(View.GONE);
                return true;
            }
            if (mIsCompleted && extra == PlayerCode.EXTRA_CODE_EMPTY_PLAYLIST) {
                Log.d(TAG, "mVideoView reconnect!!!");
                mVideoView.removeCallbacks(mVideoReconnect);
                mVideoReconnect = new Runnable() {
                    @Override
                    public void run() {
                        mVideoView.setVideoPath(mVideoPath);
                    }
                };
                mVideoView.postDelayed(mVideoReconnect, mReqDelayMills);
                mReqDelayMills += 200;
            } else if (extra == PlayerCode.EXTRA_CODE_404_NOT_FOUND) {
                // NO ts exist
                if (mBufferingIndicator != null)
                    mBufferingIndicator.setVisibility(View.GONE);
            } else if (extra == PlayerCode.EXTRA_CODE_IO_ERROR) {
                // NO rtmp stream exist
                if (mBufferingIndicator != null)
                    mBufferingIndicator.setVisibility(View.GONE);
            }
        }
        // return true means you handle the onError, hence System wouldn't handle it again(popup a dialog).
        return true;
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
        Log.d(TAG, "onInfo what=" + what + ", extra=" + extra);


        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            Log.i(TAG, "onInfo: (MEDIA_INFO_BUFFERING_START)");
            if (mBufferingIndicator != null)
                mBufferingIndicator.setVisibility(View.VISIBLE);
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {

            Log.i(TAG, "onInfo: (MEDIA_INFO_BUFFERING_END)");
            if (mBufferingIndicator != null)
                mBufferingIndicator.setVisibility(View.GONE);
        } else if (what == IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START) {
//            Toast.makeText(this, "Audio Start", Toast.LENGTH_LONG).show();
            Log.i(TAG, "duration:" + mVideoView.getDuration());
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
//            Toast.makeText(this, "Video Start", Toast.LENGTH_LONG).show();

            Log.i(TAG, "duration:" + mVideoView.getDuration());
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        if (mVideoView != null) {
            mVideoView.stopPlayback();
        }
        App.getProxy(this).unregisterCacheListener(this);
        super.onDestroy();
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        Log.d(TAG, "onPrepared");
        mBufferingIndicator.setVisibility(View.GONE);
        mReqDelayMills = REQ_DELAY_MILLS;
    }

    @Override
    public void onResume() {
        super.onResume();
        mReqDelayMills = REQ_DELAY_MILLS;
        if (mVideoView != null && !mIsLiveStream && mLastPosition != 0) {
            mVideoView.seekTo(mLastPosition);
            mVideoView.start();
        }
    }

    @Override
    public void onPause() {
        if (mVideoView != null) {
            mLastPosition = mVideoView.getCurrentPosition();
            mVideoView.pause();
        }
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int width, int height, int sarNum, int sarDen) {
        Log.i(TAG, "onVideoSizeChanged " + iMediaPlayer.getVideoWidth() + "x" + iMediaPlayer.getVideoHeight() + ",width:" + width + ",height:" + height + ",sarDen:" + sarDen + ",sarNum:" + sarNum);
        if (width > height) {
            // land video
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            mScreenSize = Util.getResolution(this);
        } else {
            // port video
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            mScreenSize = Util.getResolution(this);
        }

        if (width < mScreenSize.first) {
            height = mScreenSize.first * height / width;
            width = mScreenSize.first;
        }

        if (width * height < mScreenSize.first * mScreenSize.second) {
            width = mScreenSize.second * width / height;
            height = mScreenSize.second;
        }

        mLayoutParams = mAspectLayout.getLayoutParams();
        mLayoutParams.width = width;
        mLayoutParams.height = height;
        mAspectLayout.setLayoutParams(mLayoutParams);
    }

    //视频缓存
    @Override
    public void onCacheAvailable(File cacheFile, String url, int percentsAvailable) {
        Log.i("wanglei", "cacheFile:" + cacheFile.getAbsolutePath() + "|url:" + url);
    }
}

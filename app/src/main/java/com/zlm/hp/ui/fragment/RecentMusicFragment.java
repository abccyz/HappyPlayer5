package com.zlm.hp.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.zlm.hp.R;
import com.zlm.hp.adapter.RecentOrLikeMusicAdapter;
import com.zlm.hp.application.HPApplication;
import com.zlm.hp.db.AudioInfoDB;
import com.zlm.hp.model.AudioInfo;
import com.zlm.hp.receiver.AudioBroadcastReceiver;
import com.zlm.hp.receiver.FragmentReceiver;

import java.util.ArrayList;
import java.util.List;

import base.utils.ThreadUtil;

/**
 * 最近音乐
 */
public class RecentMusicFragment extends BaseFragment {
    private ArrayList<AudioInfo> mDatas;

    /**
     * 列表视图
     */
    private RecyclerView mRecyclerView;
    //
    private RecentOrLikeMusicAdapter mAdapter;

    private Runnable runnable;

    private static final int LOADDATA = 0;


    /**
     *
     */
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LOADDATA:
                    loadDataUtil(0);
                    break;
            }
        }
    };
    private AudioBroadcastReceiver mAudioBroadcastReceiver;

    /**
     * 广播监听
     */
    private AudioBroadcastReceiver.AudioReceiverListener mAudioReceiverListener = new AudioBroadcastReceiver.AudioReceiverListener() {
        @Override
        public void onReceive(Context context, Intent intent) {
            doAudioReceive(context, intent);
        }
    };

    public RecentMusicFragment() {

    }

    @Override
    protected int setContentViewId() {
        return R.layout.layout_fragment_recent_music;
    }

    @Override
    protected void initViews(Bundle savedInstanceState, View mainView) {
        TextView titleView = mainView.findViewById(R.id.title);
        titleView.setText(R.string.recent_play);

        //返回
        RelativeLayout backImg = mainView.findViewById(R.id.backImg);
        backImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //
                Intent closeIntent = new Intent(FragmentReceiver.ACTION_CLOSEDFRAGMENT);
                closeIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                mActivity.sendBroadcast(closeIntent);

            }
        });

        //
        mRecyclerView = mainView.findViewById(R.id.recent_recyclerView);
        //初始化内容视图
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity.getApplicationContext()));

        //
        mDatas = new ArrayList<AudioInfo>();
        mAdapter = new RecentOrLikeMusicAdapter(mActivity, mDatas, true);
        mRecyclerView.setAdapter(mAdapter);

        showLoadingView();

        //
        //注册监听
        mAudioBroadcastReceiver = new AudioBroadcastReceiver(mActivity.getApplicationContext());
        mAudioBroadcastReceiver.setAudioReceiverListener(mAudioReceiverListener);
        mAudioBroadcastReceiver.registerReceiver(mActivity.getApplicationContext());
    }

    /**
     * 处理音频监听事件
     *
     * @param context
     * @param intent
     */
    private void doAudioReceive(Context context, Intent intent) {
        String action = intent.getAction();


        if (action.equals(AudioBroadcastReceiver.ACTION_NULLMUSIC)) {
            mAdapter.reshViewHolder(null, false);
        } else if (action.equals(AudioBroadcastReceiver.ACTION_INITMUSIC)) {
            //初始化
            // AudioMessage audioMessage = (AudioMessage) intent.getSerializableExtra(AudioMessage.KEY);
            AudioInfo audioInfo = HPApplication.getInstance().getCurAudioInfo();//audioMessage.getAudioInfo();
            mAdapter.reshViewHolder(audioInfo, true);
        }
    }

    @Override
    protected void loadData(boolean isRestoreInstance) {
        if (isRestoreInstance) {

            mDatas.clear();
        }
        mHandler.sendEmptyMessageDelayed(LOADDATA, 300);

    }

    /**
     * 加载数据
     */
    private void loadDataUtil(int sleepTime) {
        mDatas.clear();
        runnable = new Runnable() {
            @Override
            public void run() {
                List<AudioInfo> data = AudioInfoDB.getAudioInfoDB(mActivity.getApplicationContext()).getAllRecentAudio();
                for (int i = 0; i < data.size(); i++) {
                    mDatas.add(data.get(i));
                }

                if (mDatas.size() > 0) {
                    mAdapter.setState(RecentOrLikeMusicAdapter.NOMOREDATA);
                } else {
                    mAdapter.setState(RecentOrLikeMusicAdapter.NODATA);
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override public void run() {
                        mAdapter.notifyDataSetChanged();
                        showContentView();
                    }  });//切换至主线程更新ui

            }
        };
        ThreadUtil.runInThread(runnable);
    }

    @Override
    public void onDestroy() {
        mAudioBroadcastReceiver.unregisterReceiver(mActivity.getApplicationContext());
        if(runnable != null) {
            ThreadUtil.cancelThread(runnable);
        }
        super.onDestroy();
    }

    @Override
    protected int setTitleViewId() {
        return R.layout.layout_title;
    }

    @Override
    protected boolean isAddStatusBar() {
        return true;
    }
}

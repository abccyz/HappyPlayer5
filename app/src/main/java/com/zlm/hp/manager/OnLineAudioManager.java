package com.zlm.hp.manager;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.zlm.down.entity.DownloadTask;
import com.zlm.down.entity.DownloadThreadInfo;
import com.zlm.down.interfaces.IDownloadTaskEvent;
import com.zlm.down.manager.DownloadTaskManager;
import com.zlm.hp.constants.ConfigInfo;
import com.zlm.hp.constants.ResourceConstants;
import com.zlm.hp.db.util.DownloadThreadInfoDB;
import com.zlm.hp.entity.AudioInfo;
import com.zlm.hp.http.APIHttpClient;
import com.zlm.hp.util.CodeLineUtil;
import com.zlm.hp.util.HttpUtil;
import com.zlm.hp.util.ResourceUtil;
import com.zlm.hp.util.ZLog;

import java.util.Date;

/**
 * @Description: 在线音频管理
 * @author: zhangliangming
 * @date: 2018-10-08 21:20
 **/
public class OnLineAudioManager {

    /**
     * 子线程用于执行耗时任务
     */
    private Handler mWorkerHandler;
    //创建异步HandlerThread
    private HandlerThread mHandlerThread;

    /**
     * 线程个数
     */
    public static final int threadNum = 1;
    /**
     * 当前任务id
     */
    private String mCurTaskId = "-1";

    /**
     * 下载管理器
     */
    private static DownloadTaskManager mDownloadTaskManager;

    /**
     *
     */
    private static Context mContext;

    public OnLineAudioManager(Context context) {

        this.mContext = context;
        //创建异步HandlerThread
        mHandlerThread = new HandlerThread("onlineDownloadTaskThread", Process.THREAD_PRIORITY_BACKGROUND);
        //必须先开启线程
        mHandlerThread.start();
        //子线程Handler
        mWorkerHandler = new Handler(mHandlerThread.getLooper());

        mDownloadTaskManager = new DownloadTaskManager(context, "onlineDownloadTaskManager", new IDownloadTaskEvent() {
            @Override
            public void taskWaiting(DownloadTask task) {

            }

            @Override
            public void taskDownloading(DownloadTask task, int downloadedSize) {
                if (task.getTaskFileSize() <= downloadedSize) {
                    return;
                }
                if (downloadedSize > 1024 * 200) {
                    //开始播放音频歌曲
                    AudioInfo audioInfo = AudioPlayerManager.newInstance(mContext).getCurSong(task.getTaskId());
                    if (audioInfo != null) {
                        AudioPlayerManager.newInstance(mContext).playDownloadingNetSong(audioInfo);
                    }
                }

                ZLog.d(new CodeLineUtil().getCodeLineInfo(), "task taskDownloading ->" + task.getTaskName() + " " + downloadedSize);
            }

            @Override
            public void taskPause(DownloadTask task, int downloadedSize) {
                if (task.getTaskFileSize() <= downloadedSize) {
                    return;
                }
                ZLog.d(new CodeLineUtil().getCodeLineInfo(), "task taskPause ->" + task.getTaskName() + " " + downloadedSize);
            }

            @Override
            public void taskCancel(DownloadTask task) {
                ZLog.d(new CodeLineUtil().getCodeLineInfo(), "task taskCancel ->" + task.getTaskName());
            }

            @Override
            public void taskFinish(DownloadTask task, int downloadedSize) {
                if (mCurTaskId.equals(task.getTaskId())) {
                    //任务完成后，重置任务id
                    mCurTaskId = "-1";
                }
                ZLog.d(new CodeLineUtil().getCodeLineInfo(), "task taskFinish ->" + task.getTaskName() + " " + downloadedSize);
            }

            @Override
            public void taskError(DownloadTask task, String msg) {

            }

            @Override
            public boolean getAskWifi() {
                ConfigInfo configInfo = ConfigInfo.obtain();
                return configInfo.isWifi();
            }

            @Override
            public int getTaskThreadDownloadedSize(DownloadTask task, int threadId) {
                if (DownloadThreadInfoDB.isExists(mContext, task.getTaskId(), threadNum, threadId)) {
                    //任务存在
                    DownloadThreadInfo downloadThreadInfo = DownloadThreadInfoDB.getDownloadThreadInfo(mContext, task.getTaskId(), threadNum, threadId);
                    if (downloadThreadInfo != null) {
                        ZLog.d(new CodeLineUtil().getCodeLineInfo(), "task getTaskThreadDownloadedSize -> 在线播放任务名称：" + task.getTaskName() + " 子任务线程id: " + threadId + " 已下载大小：" + downloadThreadInfo.getDownloadedSize());
                        return downloadThreadInfo.getDownloadedSize();
                    }
                }
                return 0;
            }

            @Override
            public void taskThreadDownloading(DownloadTask task, int threadId, int downloadedSize) {

                DownloadThreadInfo downloadThreadInfo = new DownloadThreadInfo();
                downloadThreadInfo.setDownloadedSize(downloadedSize);
                downloadThreadInfo.setThreadId(threadId);
                downloadThreadInfo.setTaskId(task.getTaskId());
                downloadThreadInfo.setThreadNum(threadNum);

                if (DownloadThreadInfoDB.isExists(mContext, task.getTaskId(), threadNum, threadId)) {
                    //任务存在
                    DownloadThreadInfoDB.update(mContext, task.getTaskId(), threadNum, threadId, downloadedSize);
                } else {
                    //任务不存在
                    DownloadThreadInfoDB.add(mContext, downloadThreadInfo);
                }
            }

            @Override
            public void taskThreadPause(DownloadTask task, int threadId, int downloadedSize) {

            }

            @Override
            public void taskThreadFinish(DownloadTask task, int threadId, int downloadedSize) {
                if (DownloadThreadInfoDB.isExists(mContext, task.getTaskId(), threadNum, threadId)) {

                    DownloadThreadInfo downloadThreadInfo = new DownloadThreadInfo();
                    downloadThreadInfo.setDownloadedSize(downloadedSize);
                    downloadThreadInfo.setThreadId(threadId);
                    downloadThreadInfo.setTaskId(task.getTaskId());
                    downloadThreadInfo.setThreadNum(threadNum);
                    //任务存在
                    DownloadThreadInfoDB.update(mContext, task.getTaskId(), threadNum, threadId, downloadedSize);
                }
            }

            @Override
            public void taskThreadError(DownloadTask task, int threadId, String msg) {

            }
        });
    }


    /**
     * 添加任务
     *
     * @param audioInfo
     */
    public synchronized void addDownloadTask(final AudioInfo audioInfo) {
        //暂停旧的任务
        pauseTask();
        //异步下载
        mWorkerHandler.post(new Runnable() {
            @Override
            public void run() {
                addTask(audioInfo);
            }
        });


    }

    /**
     * @param audioInfo
     */
    private void addTask(AudioInfo audioInfo) {

        APIHttpClient apiHttpClient = HttpUtil.getHttpClient();
        ConfigInfo configInfo = ConfigInfo.obtain();

        apiHttpClient.getSongInfo(mContext, audioInfo.getHash(), audioInfo, configInfo.isWifi());

        DownloadTask downloadTask = new DownloadTask();
        downloadTask.setTaskName(audioInfo.getTitle());
        downloadTask.setTaskExt(audioInfo.getFileExt());
        downloadTask.setTaskId(audioInfo.getHash());

        String fileName = audioInfo.getTitle();
        //String taskPath = ResourceUtil.getFilePath(mContext, ResourceConstants.PATH_AUDIO, fileName + "." + downloadTask.getTaskExt());
        String taskTempPath = ResourceUtil.getFilePath(mContext, ResourceConstants.PATH_CACHE_AUDIO, audioInfo.getHash() + ".temp");

//        downloadTask.setTaskPath(taskPath);
        downloadTask.setTaskTempPath(taskTempPath);
        downloadTask.setTaskUrl(audioInfo.getDownloadUrl());
        downloadTask.setThreadNum(threadNum);
        downloadTask.setCreateTime(new Date());

        mDownloadTaskManager.addDownloadTask(downloadTask);
    }

    /**
     * 暂停任务
     *
     * @param
     */
    public synchronized void pauseTask() {
        //暂停旧的任务
        if (!mCurTaskId.equals("-1")) {
            mDownloadTaskManager.pauseDownloadTask(mCurTaskId);
        }
    }


    /**
     * 释放
     */
    public void release() {
        //移除队列任务
        if (mWorkerHandler != null) {
            mWorkerHandler.removeCallbacksAndMessages(null);
        }

        //关闭线程
        if (mHandlerThread != null)
            mHandlerThread.quit();
    }
}

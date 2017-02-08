package com.dingmouren.dingdingmusic.ui.musicplay;

import android.animation.Animator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.design.widget.Snackbar;
import android.support.percent.PercentRelativeLayout;
import android.support.v4.view.ViewPager;
import android.transition.Fade;
import android.transition.Slide;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.dingmouren.dingdingmusic.Constant;
import com.dingmouren.dingdingmusic.MyApplication;
import com.dingmouren.dingdingmusic.R;
import com.dingmouren.dingdingmusic.base.BaseActivity;
import com.dingmouren.dingdingmusic.bean.MusicBean;
import com.dingmouren.dingdingmusic.service.MediaPlayerService;
import com.dingmouren.dingdingmusic.utils.SPUtil;
import com.dingmouren.greendao.MusicBeanDao;
import com.jiongbull.jlog.JLog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import jp.wasabeef.glide.transformations.BlurTransformation;

/**
 * Created by dingmouren on 2017/1/17.
 */

public class PlayingActivity extends BaseActivity {

    private static final String TAG = PlayingActivity.class.getName();

    @BindView(R.id.seek_bar)  SeekBar mSeekBar;
    @BindView(R.id.tv_song_name) TextView mTvSongName;
    @BindView(R.id.tv_singer) TextView mTvSinger;
    @BindView(R.id.album_viewpager) ViewPager mAlbumViewPager;
    @BindView(R.id.btn_playorpause) ImageButton mBtnPlay;
    @BindView(R.id.btn_single) ImageButton mPlayMode;
    @BindView(R.id.img_bg) ImageView mImgBg;
    @BindView(R.id.contanier_play_activity) PercentRelativeLayout mRootLayout;

    public Messenger mServiceMessenger;//来自服务端的Messenger
    private boolean isConnected = false;//标记是否连接上了服务端
    private float mPercent;//进度的百分比
    private AlbumFragmentAdapater mAlbumFragmentAdapater;//专辑图片的适配器
    public int position;//本地歌曲正在播放的歌曲位置，否则就是其他集合的第一首歌曲
    public String flag;//歌曲集合的类型
    public int currentTime;//实时当前进度
    public int duration;//歌曲的总进度
    private float mPositionOffset;//viewpager滑动的百分比
    private int mState;//viewpager的滑动状态
    List<MusicBean> list ;
    private int enterX;//传递过来的x坐标，是点击View的中心点的x坐标，揭露动画
    private int enterY;//传递过来的y坐标，是点击View的中心点的y坐标，揭露动画
    @Override
    public int setLayoutResourceID() {
        return R.layout.activity_musicplayer;
    }

    @Override
    public void init(Bundle savedInstanceState) {
        setTransiton();
        bindService(new Intent(getApplicationContext(),MediaPlayerService.class),mServiceConnection, BIND_AUTO_CREATE);
    }


    private void setTransiton() {
        Slide slide = new Slide(Gravity.BOTTOM);
        slide.setDuration(700);

        getWindow().setEnterTransition(slide);

        Slide slide1 = new Slide();
        slide1.setDuration(900);
        slide1.setSlideEdge(Gravity.TOP);
        getWindow().setReturnTransition(slide1);
    }


    @Override
    public void initView() {
        updatePlayMode();
        mSeekBar.setMax(100);

        mAlbumFragmentAdapater = new AlbumFragmentAdapater(getSupportFragmentManager());
        mAlbumViewPager.setAdapter(mAlbumFragmentAdapater);
        mAlbumViewPager.setOffscreenPageLimit(6);

        //揭露动画
        mRootLayout.post(new Runnable() {
            @Override
            public void run() {
                enterX = getIntent().getIntExtra("x",0);
                enterY = getIntent().getIntExtra("y",0);
                if (0 != enterX && 0 != enterY){
                    Animator animator = createRevealAnimator(false,enterX,enterY);
                    animator.start();
                }
            }
        });
//        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }


    @Override
    public void initListener() {
        //进度条的监听
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser){//判断来自用户的滑动
                    mPercent = (float) progress * 100 /(float) mSeekBar.getMax();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //用户松开SeekBar，通知MediaPlayerService更新播放器的进度，解决拖动过程中卡顿的问题
                Message msgToMediaPlayerService = Message.obtain();
                msgToMediaPlayerService.what  = Constant.PLAYING_ACTIVITY_CUSTOM_PROGRESS;
                msgToMediaPlayerService.arg1 = (int) mPercent;
                try {
                    mServiceMessenger.send(msgToMediaPlayerService);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        //滑动播放上/下一首歌曲的监听,实际上传递过去的是歌曲的position
        mAlbumViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {//arg1:当前页面的位置，也就是position;     arg2:当前页面偏移的百分比;     arg3当前页面偏移的像素位置
                JLog.e(TAG,"onPageScrolled--postion:" + position +" positionOffset:"+positionOffset+" positionOffsetPixels:"+positionOffsetPixels);
                mPositionOffset = positionOffset;
                if (position == 0){//解决第一次进入的时候没有显示模糊效果
                    Glide.with(PlayingActivity.this)//底部的模糊效果
                            .load(list.get(position).getAlbumpic_big())
                            .bitmapTransform(new BlurTransformation(PlayingActivity.this,99))
                            .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                            .crossFade()
                            .into(mImgBg);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {//state==1表示正在滑动，state==2表示滑动完毕，state==0表示没有动作
                JLog.e(TAG,"onPageScrollStateChanged--state:" + state);
                mState = state;
            }
            @Override
            public void onPageSelected(int position) {
                if (2 == mState && 0 < mPositionOffset) {
                    Message msgToService = Message.obtain();
                    msgToService.arg1 = position;
                    msgToService.what = Constant.PLAYING_ACTIVITY_PLAYING_POSITION;
                    if (null != mServiceMessenger) {
                        try {
                            mServiceMessenger.send(msgToService);
                            JLog.e(TAG, "onPageSelected--postion:" + position + " 发送播放上/下一首歌曲的消息");
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }

                Glide.with(PlayingActivity.this)//底部的模糊效果
                        .load(list.get(position).getAlbumpic_big()==null ? R.mipmap.native_1 : list.get(position).getAlbumpic_big())
                        .bitmapTransform(new BlurTransformation(PlayingActivity.this,99))
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .crossFade()
                        .into(mImgBg);
            }
        });
    }

    @Override
    public void initData() {

    }

    @OnClick({ R.id.btn_playorpause,R.id.btn_single})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_playorpause://播放or暂停
                if (null != mServiceMessenger) {
                    Message msgToServicePlay = Message.obtain();
                    msgToServicePlay.arg1 = 0x40001;//表示这个暂停是由点击按钮造成的，
                    msgToServicePlay.what = Constant.PLAYING_ACTIVITY_PLAY;
                    try {
                        mServiceMessenger.send(msgToServicePlay);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.btn_single://顺序播放还是单曲循环
                Message msgToServceSingle = Message.obtain();
                msgToServceSingle.what = Constant.PLAYING_ACTIVITY_SINGLE;
                try {
                    mServiceMessenger.send(msgToServceSingle);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                break;

        }
    }

    ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.e(TAG,"onServiceConnected");
            mServiceMessenger = new Messenger(iBinder);
            isConnected = true;
            //用于在服务端初始化来自客户端的Messenger对象,连接成功的时候，就进行初始化
            if (null != mServiceMessenger){
                Message msgToService = Message.obtain();
                msgToService.replyTo = mPlaygingClientMessenger;
                msgToService.what = Constant.PLAYING_ACTIVITY;
                if (0 != currentTime ) {//当前进度不是0，就更新MediaPlayerService的当前进度
                    msgToService.arg1 = currentTime;
                }
                try {
                    mServiceMessenger.send(msgToService);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            //连接成功的时候，
            position = getIntent().getIntExtra("position",0);
            flag = getIntent().getStringExtra("flag");
            JLog.e(TAG,"positon:" + position +" flag:"+flag);
            if (null != mServiceMessenger  && null != flag ){
                Message msgToService = Message.obtain();
                msgToService.arg1 = position;

                if ( flag.equals(Constant.MUSIC_LOCAL)){
                    list = MyApplication.getDaoSession().getMusicBeanDao().queryBuilder().where(MusicBeanDao.Properties.Type.eq(Constant.MUSIC_LOCAL)).list();
                }else if (flag.equals(Constant.MAIN_RANDOM)){
                    list = MyApplication.getDaoSession().getMusicBeanDao().loadAll();
                    Collections.shuffle(list);
                }else if (flag.equals(Constant.MUSIC_KOREA)){
                    list = MyApplication.getDaoSession().getMusicBeanDao().queryBuilder().where(MusicBeanDao.Properties.Type.eq(Constant.MUSIC_KOREA)).list();
                }else if (flag.equals(Constant.MUSIC_ROCK)){
                    list = MyApplication.getDaoSession().getMusicBeanDao().queryBuilder().where(MusicBeanDao.Properties.Type.eq(Constant.MUSIC_ROCK)).list();
                }else if (flag.equals(Constant.MUSIC_VOLKSLIED)){
                    list = MyApplication.getDaoSession().getMusicBeanDao().queryBuilder().where(MusicBeanDao.Properties.Type.eq(Constant.MUSIC_VOLKSLIED)).list();
                }
                if (null != list) {
                    for (int i = 0; i < list.size(); i++) {
                        JLog.e(TAG,list.get(i).getSongname() +"--"+ list.get(i).getUrl());
                    }
                    //更新专辑图片
                    mAlbumFragmentAdapater.addList(list);
                    mAlbumFragmentAdapater.notifyDataSetChanged();
                    //传递歌曲集合数据
                    Bundle songsData = new Bundle();
                    songsData.putSerializable(Constant.PLAYING_ACTIVITY_DATA_KEY, (Serializable) list);
                    msgToService.setData(songsData);
                    msgToService.what = Constant.PLAYING_ACTIVITY_INIT;
                    try {
                        mServiceMessenger.send(msgToService);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            JLog.e(TAG,"onServiceDisconnected");
            isConnected = false;
        }
    };

    Messenger mPlaygingClientMessenger = new Messenger(new Handler(){
        @Override
        public void handleMessage(Message msgFromService) {
            switch (msgFromService.what){
                case Constant.MEDIA_PLAYER_SERVICE_PROGRESS://更新进度条
                    currentTime = msgFromService.arg1;
                    duration = msgFromService.arg2;
                    if (0 == duration) break;
                    mSeekBar.setProgress(currentTime * 100 /duration);
                    break;
                case Constant.MEDIA_PLAYER_SERVICE_SONG_PLAYING:
                    Bundle bundle = msgFromService.getData();
                    list = (List<MusicBean>) bundle.getSerializable(Constant.MEDIA_PLAYER_SERVICE_MODEL_PLAYING);
                    if (null != list && 0 < list.size()) {
                        mTvSongName.setText(list.get(msgFromService.arg1).getSongname());
                        mTvSinger.setText(list.get(msgFromService.arg1).getSingername());

                        //更新专辑图片
                        mAlbumFragmentAdapater.addList(list);
                        mAlbumFragmentAdapater.notifyDataSetChanged();
                        mAlbumViewPager.setCurrentItem(msgFromService.arg1,false);
                    }
                    break;
                case Constant.MEDIA_PLAYER_SERVICE_IS_PLAYING:
                    if (1 == msgFromService.arg1){//正在播放
                        mBtnPlay.setImageResource(R.mipmap.play);
                    }else {
                        mBtnPlay.setImageResource(R.mipmap.pause);
                    }
                    break;
                case Constant.PLAYING_ACTIVITY_PLAY_MODE://显示播放器的播放模式
                   updatePlayMode();
                    break;
                case Constant.MEDIA_PLAYER_SERVICE_UPDATE_SONG://播放完成自动播放下一首时，更新正在播放UI
                    int positionPlaying = msgFromService.arg1;
                    mAlbumViewPager.setCurrentItem(positionPlaying,false);
                    JLog.e(TAG,"更新正在播放的UI");

            }
            super.handleMessage(msgFromService);
        }
    });

    /**
     * 修改播放模式的UI
     */
    private void updatePlayMode(){
        int playMode = (int) SPUtil.get(MyApplication.mContext,Constant.SP_PLAY_MODE,0);
        if (0 == playMode){
            mPlayMode.setImageResource(R.mipmap.order_mode);
        }else if (1 == playMode){
            mPlayMode.setImageResource(R.mipmap.single_mode);
        }
    }

    /**
     * 揭露动画
     */
    private Animator createRevealAnimator( boolean reversed,int x, int y) {
        float hypot = (float) Math.hypot(mRootLayout.getHeight(),mRootLayout.getWidth());
        float startRadius = reversed ? hypot : 0;
        float endRadius = reversed ? 0 : hypot;

        Animator animator = ViewAnimationUtils.createCircularReveal(mRootLayout,x,y,startRadius,endRadius);
        animator.setDuration(800);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        if (reversed){
            animator.addListener(animatorListener);
        }
        return animator;
    }
    private Animator.AnimatorListener animatorListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mRootLayout.setVisibility(View.INVISIBLE);
            finish();
        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }
    };
    @Override
    public void onBackPressed() {
        if (enterX != 0 && enterY != 0) {
            Animator animator = createRevealAnimator(true, enterX, enterY);
            animator.start();
        }else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);
        JLog.e(TAG,"onDestroy");
        super.onDestroy();
    }
}

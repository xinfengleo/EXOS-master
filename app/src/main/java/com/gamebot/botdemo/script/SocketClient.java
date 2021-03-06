package com.gamebot.botdemo.script;

import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.gamebot.botdemo.utils.DateUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;

import static android.os.Looper.getMainLooper;

/**
 * Created by dzy on 2018/11/21.
 */

public class SocketClient {
    private final static String TAG = "SocketClient";
    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private String mIp;
    private int mPort;
    private boolean disconnectCheckEnabled = false;
    private volatile String lastMessage;
    private boolean disconnectChecking=false;
    private boolean connectedBack=false;
    private File file;

    public boolean connect() {
        try {
            if(socket!=null){
                socket.close();
                socket=null;
            }
            if(writer!=null){
                writer.close();
                writer=null;
            }
            if(reader!=null){
                reader.close();
                reader=null;
            }
            socket = new Socket(mIp, mPort);
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Log.e(TAG, "连接成功");
            return true;
        } catch (Exception e) {
            //e.printStackTrace();
            //Log.e(TAG, e.getMessage());
        }
        return false;
    }

    public interface connectCallback{
        void isConnect();
    }

    public SocketClient(String ip, int port) {
        this.mPort = port;
        this.mIp = ip;
        connect();
        new Thread(new MessageCheckTh()).start();
    }



    public void disconnect() {
        disconnectCheckEnabled = false;
        try {
            if(socket!=null){
                socket.close();
            }
            if(writer!=null){
                writer.close();
            }
            if(reader!=null){
                reader.close();
            }
            writer=null;
            reader=null;
            socket=null;
            Log.e(TAG, "斷開連接");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private class MessageCheckTh implements Runnable{
        @Override
        public void run() {
            while (true) {
                try {
                    if (socket!=null && !socket.isClosed() && socket.isConnected()) {
                        char ch[]=new char[30000];
                        int len = reader.read(ch);
                        if (len>-1) {
                            //socket.close();
                            //reader.close();
                            //writer.close();
                            lastMessage = new String(ch);
                            Log.e(TAG, "run: " + lastMessage);
                        }
                        Thread.sleep(1);
                    } else {
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    //Log.e(TAG,e.getMessage());
                }
            }
        }
    }

    private class DisconnectCheckTh implements Runnable{
        @Override
        public void run() {
            disconnectChecking=true;
            while (disconnectCheckEnabled) {
                if (socket == null || socket.isClosed() || !socket.isConnected() || isServerClose()) {
                    Log.e(TAG, "重新连接中");
                    connect();
                } else {
                    //Log.d(TAG, "已连接");
                    if (connectedBack) {
                        disconnectCheckEnabled = false;
                        break;
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            disconnectChecking=false;
        }
    }

    public void startDisconnectCheck(boolean connectedBack) {
        this.connectedBack=connectedBack;
        disconnectCheckEnabled = true;
        if(disconnectChecking){
            return;
        }
        new Thread(new DisconnectCheckTh()).start();
    }


    public boolean isServerClose() {
        try {
            socket.sendUrgentData(0);//发送1个字节的紧急数据，默认情况下，服务器端没有开启紧急数据处理，不影响正常通信
            return false;
        } catch (Exception se) {
            return true;
        }
    }

    public boolean isConnected() {
        return !(socket == null || socket.isClosed() || !socket.isConnected() || isServerClose());
    }

    public boolean sendMessage(String msg) {
        if (socket != null && !socket.isClosed() && socket.isConnected()) {
            try {
                writer.write(msg); // 写一个UTF-8的信息
                writer.flush();
                return true;
            } catch (IOException e) {
                Log.e(TAG,e.getMessage());
                connect();
            }
        }
        return false;
    }

    public boolean sendMessage(JsonObject msg) {
        return sendMessage(msg.toString());
    }

    public String sendMessageResult(String msg) {
        lastMessage = null;
        if (sendMessage(msg)) {
            try {
                int dt = 0;
                while (true) {
                    if (lastMessage != null) {
                        int end=lastMessage.indexOf("\n");
                        return lastMessage.substring(0,end);
                    } else if (++dt > 1000) {
                        return null;
                    }
                    //Log.d(TAG, "getResult");
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public <T> T sendMessageResult(String msg, Class<T> cls) {
        String result = sendMessageResult(msg);
        if (StringUtils.isNotEmpty(result)) {
            return (T) (new Gson().fromJson(result, cls));
        } else {
            return null;
        }
    }

    public <T> T sendMessageResult(JsonObject msg, Class<T> cls) {
        return sendMessageResult(msg.toString(), cls);
    }
    public void fileWriter(String str, File file) throws IOException{
        FileWriter writer = new FileWriter(file,true);
        // 向文件写入内容
        writer.write(DateUtil.getNowTimestampStr() + "  " + str + "\r\n");
        writer.flush();
        writer.close();
    }

}

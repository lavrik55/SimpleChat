package ru.geekbrains.chat.network;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;

public class TCPConnection {

    private final Socket socket; //
    private final Thread rxThread; //постоянно слушает входящий поток, и если что-то прилетело, то вызываем событие
    private final TCPConnectionListener eventListener;
    private final BufferedReader in;
    private final BufferedWriter out;

    public TCPConnection(TCPConnectionListener eventListener, String ipAddr, int port) throws  IOException {
        this(eventListener, new Socket(ipAddr, port));
    }

    public TCPConnection(TCPConnectionListener eventListener, Socket socket) throws IOException {
        this.eventListener = eventListener;
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), Charset.forName("UTF-8")));
        //в java потоки ввода-вывода так устроены, что разные потоки имеют разную функциональность,
        //и чтобы получить функциональность BufferedReader, нужно обернуть два раза самый простой InputStream
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), Charset.forName("UTF-8")));
        rxThread = new Thread(new Runnable() {
            //анонимный класс, который реализует интерфейс Runnable, оверрайдим метод run и создаем экземпляр
            @Override
            public void run() {
                //слушаем входящие соединения
                try {
                    eventListener.onConnectionReady(TCPConnection.this);
                    //такой конструкцией мы передаем экземпляр класса, обрамляющий текущий
                    while (!rxThread.isInterrupted()) {
                        eventListener.onReceiveString(TCPConnection.this, in.readLine());
                    }


                } catch (IOException e) {
                    eventListener.onException(TCPConnection.this, e);
                }
                finally {
                    //нужно реализовать так, чтобы класс отрабатывал и для клиентской стороны, и для серверной
                    eventListener.onDisconnect(TCPConnection.this);
                }
            }
        });
        //например в Thread можно передать экземпляр какого-нибудь класса, который реализует интерфейс Runnable

        rxThread.start();
    }

    public synchronized void sendString(String value) {
        //чтобы метод был потокобезопасным, чтобы методом можно было воспользоваться из любого потока
        try {
            out.write(value + "\r\n");
            //т.к. у нас BufferedStream, то вероятно строка записана в какой-то буфер, а нам нужно,
            //чтобы она пошла по сети дальше
            //сбрасывает все буферы и отправляет
            out.flush();
        } catch (IOException e) {
            eventListener.onException(TCPConnection.this, e);
            disconnect();
        }
    }

    public synchronized void disconnect() {
        rxThread.interrupt();
        try {
            socket.close();
        } catch (IOException e) {
            eventListener.onException(TCPConnection.this, e);
        }
    }

    @Override
    public String toString() {
        return "TCPConnection: " + socket.getInetAddress() + ": " + socket.getPort();
    }
}

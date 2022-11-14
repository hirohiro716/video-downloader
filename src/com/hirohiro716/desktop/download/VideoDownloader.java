package com.hirohiro716.desktop.download;

import java.awt.Color;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hirohiro716.StringObject;
import com.hirohiro716.gui.Border;
import com.hirohiro716.gui.GUI;
import com.hirohiro716.gui.Window;
import com.hirohiro716.gui.control.Button;
import com.hirohiro716.gui.control.ContextMenu;
import com.hirohiro716.gui.control.HorizontalPane;
import com.hirohiro716.gui.control.Label;
import com.hirohiro716.gui.control.ScrollPane;
import com.hirohiro716.gui.control.Spacer;
import com.hirohiro716.gui.control.VerticalPane;
import com.hirohiro716.gui.dialog.InstantMessage;
import com.hirohiro716.gui.dialog.ProcessAfterDialogClosing;
import com.hirohiro716.gui.dialog.TextAreaDialog;
import com.hirohiro716.gui.event.EventHandler;
import com.hirohiro716.gui.event.FrameEvent;
import com.hirohiro716.gui.event.MouseEvent;
import com.hirohiro716.gui.event.MouseEvent.MouseButton;

/**
 * youtube-dlのGUIアプリケーション。
 * 
 * @author hiro
 */
public class VideoDownloader {
    
    private static String downloadLocation = System.getProperty("user.home") + "/Downloads/";
    
    private static Window window;
    
    private static VerticalPane paneOfProgress = new VerticalPane();
    
    /**
     * URLを指定して進捗状況のコントロールを作成する。
     * 
     * @param url
     */
    private static void createControlsOfProgress(String url) {
        VerticalPane verticalPane = new VerticalPane();
        verticalPane.setName(url);
        verticalPane.setPadding(15);
        verticalPane.setBorder(Border.createLine(Color.LIGHT_GRAY, 0, 0, 1, 0));
        verticalPane.setFillChildToPaneWidth(true);
        VideoDownloader.paneOfProgress.getChildren().add(verticalPane);
        // Title
        Label labelOfTitle = new Label("-");
        labelOfTitle.setName("title");
        verticalPane.getChildren().add(labelOfTitle);
        // URL
        Label labelOfUrl = new Label(url);
        labelOfUrl.setName("url");
        verticalPane.getChildren().add(labelOfUrl);
        // Progress
        Label labelOfProgress = new Label("0%");
        labelOfProgress.setName("progress");
        verticalPane.getChildren().add(labelOfProgress);
        VideoDownloader.progresses.put(url, 0);
        // Context menu
        ContextMenu contextMenu = new ContextMenu(labelOfUrl);
        contextMenu.addContextMenuItem("URLをコピー", new Runnable() {
            
            @Override
            public void run() {
                GUI.setClipboardString(url);
            }
        });
        labelOfUrl.addMouseClickedEventHandler(MouseButton.BUTTON3, new EventHandler<MouseEvent>() {
            
            @Override
            protected void handle(MouseEvent event) {
                contextMenu.show(event.getX(), event.getY());
            }
        });
    }
    
    private static Map<String, Integer> progresses = new HashMap<>();
    
    /**
     * URLと1行の標準出力を指定して進捗状況を更新する。
     * 
     * @param url
     * @param line
     */
    private static void updateProgress(String url, String line) {
        VerticalPane verticalPane = VideoDownloader.paneOfProgress.getChildren().findControlByName(url);
        StringObject lineObject = new StringObject(line);
        if (lineObject.toString().indexOf("[download] Destination:") == 0) {
            Label labelOfTitle = verticalPane.getChildren().findLabelByName("title");
            lineObject.replace("\\[download\\] Destination:", "").replace(VideoDownloader.downloadLocation, "").trim();
            labelOfTitle.setText(lineObject.toString());
            return;
        }
        if (lineObject.toString().indexOf("[download]  ") == 0 || lineObject.toString().indexOf("[download] 1") == 0) {
            lineObject.extract("[0-9\\.]{1,}%").replace("%", "");
            Integer progress = lineObject.toInteger();
            if (progress != null) {
                Label labelOfProgress = verticalPane.getChildren().findLabelByName("progress");
                StringObject text = StringObject.repeat("|", progress);
                text.append(" ");
                text.append(progress);
                text.append("%");
                labelOfProgress.setText(text.toString());
                VideoDownloader.progresses.put(url, progress);
                return;
            }
        }
    }
    
    private static List<String> runningUrls = new ArrayList<>();
    
    private static boolean isCanceled = false;
    
    /**
     * 指定されたURLからダウンロードする。
     * 
     * @param url 
     */
    private static void downloadFromUrl(String url) {
        Thread thread = new Thread(new Runnable() {
            
            @Override
            public void run() {
                while (runningUrls.size() >= 3) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException exception) {
                    }
                }
                synchronized (VideoDownloader.runningUrls) {
                    VideoDownloader.runningUrls.add(url);
                }
                try {
                    if (VideoDownloader.isCanceled) {
                        return;
                    }
                    List<String> arguments = new ArrayList<>();
                    arguments.add("yt-dlp");
                    arguments.add("--newline");
                    arguments.add("-c");
                    arguments.add("-f");
                    arguments.add("b");
                    arguments.add("-o");
                    arguments.add(downloadLocation + "%(title)s.%(ext)s");
                    arguments.add(url);
                    Process process = Runtime.getRuntime().exec(arguments.toArray(new String[] {}));
                    InputStream inputStream = process.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder output = new StringBuilder();
                    String line = reader.readLine();
                    while (line != null) {
                        output.append(line);
                        output.append("\n");
                        line = reader.readLine();
                        if (line != null) {
                            final String lineForUpdate = line;
                            GUI.executeLater(new Runnable() {
                                
                                @Override
                                public void run() {
                                    VideoDownloader.updateProgress(url, lineForUpdate);
                                }
                            });
                        }
                    }
                    process.waitFor();
                    synchronized (VideoDownloader.runningUrls) {
                        VideoDownloader.runningUrls.remove(url);
                    }
                    for (Integer progress : VideoDownloader.progresses.values()) {
                        if (progress < 100) {
                            return;
                        }
                    }
                    InstantMessage.show("ダウンロードが完了しました。", 5000, VideoDownloader.window);
                } catch (IOException | InterruptedException exception) {
                    exception.printStackTrace();
                }
            }
        });
        thread.start();
    }
    
    /**
     * アプリケーションの開始。
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // Style
        GUI.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        GUI.setFontSizeToAdd(4);
        Class<?> toolkitClass = Toolkit.getDefaultToolkit().getClass();
        if (toolkitClass.getName().equals("sun.awt.X11.XToolkit")) {
            Field awtAppClassName = toolkitClass.getDeclaredField("awtAppClassName");
            awtAppClassName.setAccessible(true);
            awtAppClassName.set(null, "youtube-dl-gui");
        }
        // Window style
        VideoDownloader.window = new Window();
        VideoDownloader.window.setTitle("Youtube-DL-GUI");
        VideoDownloader.window.setSize(700, 600);
        VideoDownloader.window.setResizable(false);
        VideoDownloader.window.addClosingEventHandler(new EventHandler<FrameEvent>() {

            @Override
            protected void handle(FrameEvent event) {
                VideoDownloader.window.setClosable(true);
                if (VideoDownloader.runningUrls.size() > 0) {
                    VideoDownloader.window.setClosable(false);
                }
            }
        });
        // Vertical pane
        VerticalPane pane = new VerticalPane();
        pane.setFillChildToPaneWidth(true);
        pane.setPadding(20);
        pane.setSpacing(20);
        VideoDownloader.window.setContent(pane);
        // Label
        Label label = new Label();
        label.setText("開始ボタンでダウンロードを開始してください。");
        label.setWrapText(true);
        pane.getChildren().add(label);
        // Scrollpane
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setBackgroundColor(null);
        scrollPane.setBorder(Border.createLine(Color.LIGHT_GRAY, 1));
        pane.getChildren().add(scrollPane);
        pane.getGrowableControls().add(scrollPane);
        // Pane of progress
        VideoDownloader.paneOfProgress.setMaximumWidth(640);
        VideoDownloader.paneOfProgress.setPadding(10);
        VideoDownloader.paneOfProgress.setFillChildToPaneWidth(true);
        VideoDownloader.paneOfProgress.setBackgroundColor(null);
        scrollPane.setContent(VideoDownloader.paneOfProgress);
        // Horizontal pane
        HorizontalPane horizontalPane = new HorizontalPane();
        horizontalPane.setSpacing(10);
        pane.getChildren().add(horizontalPane);
        // Spacer
        Spacer spacer = new Spacer(0, 0);
        horizontalPane.getChildren().add(spacer);
        horizontalPane.getGrowableControls().add(spacer);
        // Cancel button
        Button cancelButton = new Button("強制終了");
        cancelButton.addMouseClickedEventHandler(new EventHandler<MouseEvent>() {
            
            @Override
            protected void handle(MouseEvent event) {
                try {
                    VideoDownloader.isCanceled = true;
                    List<String> arguments = new ArrayList<>();
                    arguments.add("killall");
                    arguments.add("yt-dlp");
                    Process process = Runtime.getRuntime().exec(arguments.toArray(new String[] {}));
                    process.waitFor();
                } catch (IOException | InterruptedException exception) {
                    exception.printStackTrace();
                }
            }
        });
        horizontalPane.getChildren().add(cancelButton);
        // Start button
        Button startButton = new Button("開始");
        startButton.addMouseClickedEventHandler(new EventHandler<MouseEvent>() {

            @Override
            protected void handle(MouseEvent event) {
                TextAreaDialog dialog = new TextAreaDialog(window);
                dialog.setTitle("URLの入力");
                dialog.setMessage("ダウンロードするURLを入力してダウンロードを開始してください。");
                dialog.getTextInputControl().setMinimumSize(560, 160);
                dialog.getTextInputControl().setPadding(5);
                dialog.getTextInputControl().setWrapText(false);
                dialog.setProcessAfterClosing(new ProcessAfterDialogClosing<String>() {
                    
                    @Override
                    public void execute(String dialogResult) {
                        if (dialogResult == null || dialogResult.length() == 0) {
                            return;
                        }
                        List<String> urls = new ArrayList<>();
                        for (String url : dialogResult.split("\n")) {
                            if (urls.contains(url) == false) {
                                urls.add(url);
                            }
                        }
                        VideoDownloader.progresses.clear();
                        VideoDownloader.paneOfProgress.getChildren().clear();
                        for (String url : urls) {
                            VideoDownloader.createControlsOfProgress(url);
                        }
                        for (String url : urls) {
                            VideoDownloader.downloadFromUrl(url);
                        }
                    }
                });
                dialog.show();
                VideoDownloader.isCanceled = false;
            }
        });
        horizontalPane.getChildren().add(startButton);
        window.show();
    }
}

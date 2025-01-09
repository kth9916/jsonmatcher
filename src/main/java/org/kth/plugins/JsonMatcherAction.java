package org.kth.plugins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.kth.entity.JsonProcessor;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.StringReader;
import java.util.Map;

public class JsonMatcherAction implements ToolWindowFactory {
    //
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // 입력을 위한 JTextArea
        JTextArea inputTextArea = new JTextArea(10, 40);
        inputTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane inputScrollPane = new JScrollPane(inputTextArea);
        inputScrollPane.setBorder(BorderFactory.createTitledBorder("Ugly JSON"));

        // 결과를 위한 JTextArea
        JTextArea outputTextArea = new JTextArea(10, 40);
        outputTextArea.setEditable(false); // 결과는 읽기 전용
        outputTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Beautiful JSON"));

        // 처리 버튼
        JButton beautifyButton = new JButton("Beautify JSON");
//        beautifyButton.setPreferredSize(new Dimension(200, 30)); // 선호 크기 설정
        beautifyButton.addActionListener(e -> {
            String jsonInput = inputTextArea.getText();
            JsonProcessor processor = new JsonProcessor();
            try {
                // 입력된 JSON을 Beautify
                String beautifiedOutputJson = processor.processJson(jsonInput);
                Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
                JsonReader reader = new JsonReader(new StringReader(jsonInput));
                reader.setLenient(true); // 엄격 모드 비활성화

                // JSON 문자열을 Map으로 변환
                Map<String, Object> tempMap = gson.fromJson(reader, Map.class);
                String beutifiedInputJson = gson.toJson(tempMap);
                textChanged(inputTextArea, beutifiedInputJson);

                // JSON 처리 후 결과를 출력
                textChanged(outputTextArea, beautifiedOutputJson);
            } catch (Exception ex) {
                outputTextArea.setText("Error beautify JSON: " + ex.getMessage());
            }
        });
        // Input JSON 클립보드 복사 버튼
        JButton copyUglyJsonButton = new JButton("Copy Ugly JSON");
        copyUglyJsonButton.setPreferredSize(new Dimension(200, 30)); // 선호 크기 설정
        copyUglyJsonButton.addActionListener(e -> copyToClipboard(inputTextArea.getText(), copyUglyJsonButton));

        // Output JSON 클립보드 복사 버튼
        JButton copyBeautifulJsonButton = new JButton("Copy Beautiful JSON");
        copyBeautifulJsonButton.setPreferredSize(new Dimension(200, 30)); // 선호 크기 설정
        copyBeautifulJsonButton.addActionListener(e -> copyToClipboard(outputTextArea.getText(), copyBeautifulJsonButton));

        // 버튼 패널 생성
        JPanel beautifyButtonPanel = new JPanel();
        beautifyButtonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        beautifyButtonPanel.setLayout(new GridLayout(1, 1));

        JPanel copyUglyJsonButtonPanel = new JPanel();
        copyUglyJsonButtonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        copyUglyJsonButtonPanel.setLayout(new GridLayout(1, 1));

        JPanel copyBeautifulJsonButtonPanel = new JPanel();
        copyBeautifulJsonButtonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        copyBeautifulJsonButtonPanel.setLayout(new GridLayout(1, 1));

        // 버튼 추가
        beautifyButtonPanel.add(beautifyButton);
        copyBeautifulJsonButtonPanel.add(copyBeautifulJsonButton);
        copyUglyJsonButtonPanel.add(copyUglyJsonButton);

        // 버튼 패널을 메인 패널에 추가
        panel.add(beautifyButtonPanel);
        panel.add(copyUglyJsonButtonPanel);
        panel.add(copyBeautifulJsonButtonPanel);

        // JSON 패널을 메인 패널에 추가
        panel.add(inputScrollPane);
        panel.add(outputScrollPane);

        // Tool Window에 콘텐츠 추가
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    private void textChanged(JTextArea textArea, String text) {
        textArea.setText(text);
        Character.Subset[] hangul = {Character.UnicodeBlock.HANGUL_SYLLABLES};
        textArea.getInputContext().setCharacterSubsets(hangul);
    }

    private void copyToClipboard(String text, JButton button) {
        String originalText = button.getText();
        StringSelection selection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);

        // 버튼 텍스트를 "Copied"로 변경
        button.setText("Copied!");

        // 2초 후에 원래 텍스트로 되돌리기
        Timer timer = new Timer(1000, e -> button.setText(originalText));
        timer.setRepeats(false); // 한 번만 실행되도록 설정
        timer.start();
    }
}

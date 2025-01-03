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
import java.nio.charset.StandardCharsets;
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
        panel.add(inputScrollPane);

        // 결과를 위한 JTextArea
        JTextArea outputTextArea = new JTextArea(10, 40);
        outputTextArea.setEditable(false); // 결과는 읽기 전용
        outputTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Beautiful JSON"));
        panel.add(outputScrollPane);

        // 처리 버튼
        JButton beautifyButton = new JButton("Beautify JSON");
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
        copyUglyJsonButton.addActionListener(e -> copyToClipboard(inputTextArea.getText()));

        // Output JSON 클립보드 복사 버튼
        JButton copyBeautifulJsonButton = new JButton("Copy Beautiful JSON");
        copyBeautifulJsonButton.addActionListener(e -> copyToClipboard(outputTextArea.getText()));

        // 버튼 패널 생성
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout()); // FlowLayout으로 가로 배치

        // 버튼 추가
        buttonPanel.add(beautifyButton);
        buttonPanel.add(copyUglyJsonButton);
        buttonPanel.add(copyBeautifulJsonButton);

        // 버튼 패널을 메인 패널에 추가
        panel.add(buttonPanel);


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

    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
        JOptionPane.showMessageDialog(null, "Copied to clipboard!");
    }
}

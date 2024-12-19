package org.kth.plugins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.kth.entity.JsonProcessor;

import javax.swing.*;
import java.io.StringReader;
import java.util.Map;

public class JsonMatcherAction implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // 입력을 위한 JTextArea
        JTextArea inputTextArea = new JTextArea(10, 40);
        JScrollPane inputScrollPane = new JScrollPane(inputTextArea);
        inputScrollPane.setBorder(BorderFactory.createTitledBorder("Input JSON"));
        panel.add(inputScrollPane);

        // 결과를 위한 JTextArea
        JTextArea outputTextArea = new JTextArea(10, 40);
        outputTextArea.setEditable(false); // 결과는 읽기 전용
        JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Processed JSON"));
        panel.add(outputScrollPane);

        // 처리 버튼
        JButton processButton = new JButton("Process JSON");
        processButton.addActionListener(e -> {
            String jsonInput = inputTextArea.getText();
            JsonProcessor processor = new JsonProcessor();
            try {
                // 입력된 JSON을 Beautify
                String beautifiedOutputJson = processor.processJson(jsonInput);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonReader reader = new JsonReader(new StringReader(jsonInput));
                reader.setLenient(true); // 엄격 모드 비활성화

                // JSON 문자열을 Map으로 변환
                Map<String, Object> tempMap = gson.fromJson(reader, Map.class);
                String beutifiedInputJson = gson.toJson(tempMap);
                inputTextArea.setText(beutifiedInputJson); // Beautified JSON을 입력 영역에 설정

                // JSON 처리 후 결과를 출력
                outputTextArea.setText(beautifiedOutputJson); // 여기서는 같은 JSON을 출력
            } catch (Exception ex) {
                outputTextArea.setText("Error processing JSON: " + ex.getMessage());
            }
        });
        panel.add(processButton);

        // Tool Window에 콘텐츠 추가
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
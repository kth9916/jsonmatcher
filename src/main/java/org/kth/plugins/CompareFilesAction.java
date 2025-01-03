package org.kth.plugins;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.kth.entity.JsonProcessor;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class CompareFilesAction implements ToolWindowFactory {
    //
    private int currentErrorIndex = -1; // 현재 오류 인덱스
    private List<Integer> errorLines; // 오류가 있는 라인 번호
    private boolean syncScrollEnabled = true;
    private boolean beutifyAmisJson = true;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Left panel for File 1
        JPanel leftPanel = new JPanel(new BorderLayout());
        JTextArea file1TextArea = new JTextArea(10, 20);
        file1TextArea.setBorder(BorderFactory.createTitledBorder("File 1"));
        JScrollPane file1ScrollPane = new JScrollPane(file1TextArea);
        leftPanel.add(file1ScrollPane, BorderLayout.CENTER);

        // Right panel for File 2
        JPanel rightPanel = new JPanel(new BorderLayout());
        JTextArea file2TextArea = new JTextArea(10, 20);
        file2TextArea.setBorder(BorderFactory.createTitledBorder("File 2"));
        JScrollPane file2ScrollPane = new JScrollPane(file2TextArea);
        rightPanel.add(file2ScrollPane, BorderLayout.CENTER);

        // Main panel to hold both left and right panels
        JPanel mainPanel = new JPanel(new GridLayout(1, 2));
        mainPanel.add(leftPanel);
        mainPanel.add(rightPanel);

        // Compare button
        JButton compareButton = new JButton("Compare");
        compareButton.addActionListener(e -> {
            try {
                highlightDifferences(file1TextArea, file2TextArea, false, beutifyAmisJson);
            } catch (BadLocationException ex) {
                throw new RuntimeException(ex);
            }
        });

        // ASC Compare button
        JButton ascCompareButton = new JButton("ASC Compare");
        ascCompareButton.addActionListener(e -> {
            try {
                highlightDifferences(file1TextArea, file2TextArea, true, beutifyAmisJson);
            } catch (BadLocationException ex) {
                throw new RuntimeException(ex);
            }
        });

        // Move to next error button
        JButton nextErrorButton = new JButton("Next Error");
        nextErrorButton.addActionListener(e -> {
            if (errorLines != null && !errorLines.isEmpty()) {
                currentErrorIndex = (currentErrorIndex + 1) % errorLines.size();
                int lineNumber = errorLines.get(currentErrorIndex);
                try {
                    file1TextArea.setCaretPosition(file1TextArea.getLineStartOffset(lineNumber));
                } catch (BadLocationException ex) {
                    throw new RuntimeException(ex);
                }
                file1TextArea.requestFocus();
                try {
                    file2TextArea.setCaretPosition(file2TextArea.getLineStartOffset(lineNumber));
                } catch (BadLocationException ex) {
                    throw new RuntimeException(ex);
                }
                file2TextArea.requestFocus();
            }
        });

        // Sync Scroll button
        JButton syncScrollButton = new JButton("Stop Sync");
        syncScrollButton.addActionListener(e -> {
            syncScrollEnabled = !syncScrollEnabled; // 스크롤 동기화 상태 토글
            syncScrollButton.setText(syncScrollEnabled ? "Stop Sync" : "Sync Scroll");
        });

        // 스크롤 동기화 리스너
        file1ScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (syncScrollEnabled) {
                file2ScrollPane.getVerticalScrollBar().setValue(file1ScrollPane.getVerticalScrollBar().getValue());
            }
        });

        file2ScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (syncScrollEnabled) {
                file1ScrollPane.getVerticalScrollBar().setValue(file2ScrollPane.getVerticalScrollBar().getValue());
            }
        });

        // 가로 스크롤 동기화
        file1ScrollPane.getHorizontalScrollBar().addAdjustmentListener(e -> {
            if (syncScrollEnabled) {
                file2ScrollPane.getHorizontalScrollBar().setValue(file1ScrollPane.getHorizontalScrollBar().getValue());
            }
        });

        file2ScrollPane.getHorizontalScrollBar().addAdjustmentListener(e -> {
            if (syncScrollEnabled) {
                file1ScrollPane.getHorizontalScrollBar().setValue(file2ScrollPane.getHorizontalScrollBar().getValue());
            }
        });

        // Beautify AMIS JSON button
        JButton beautifyAmisJsonButton = new JButton("Ugly");
        beautifyAmisJsonButton.addActionListener(e -> {
            beutifyAmisJson = !beutifyAmisJson; //
            beautifyAmisJsonButton.setText(beutifyAmisJson ? "Ugly" : "Beautify");
        });


        // Button panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(compareButton);
        buttonPanel.add(ascCompareButton);
        buttonPanel.add(nextErrorButton);
        buttonPanel.add(syncScrollButton);
        buttonPanel.add(beautifyAmisJsonButton);

        // Add everything to the main panel
        panel.add(mainPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Tool Window에 콘텐츠 추가
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "File Compare", false);
        toolWindow.getContentManager().addContent(content);
    }

    private void highlightDifferences(
            JTextArea file1TextArea,
            JTextArea file2TextArea,
            boolean ascending,
            boolean beautifyAmisJson
    ) throws BadLocationException {
        // 기존 하이라이팅 제거
        file1TextArea.getHighlighter().removeAllHighlights();
        file2TextArea.getHighlighter().removeAllHighlights();

        if (beautifyAmisJson) {
            JsonProcessor processor = new JsonProcessor();
            String content1 = file1TextArea.getText();
            String content2 = file2TextArea.getText();
            String beautifulJson1 = processor.processJson(content1);
            String beautifulJson2 = processor.processJson(content2);
            file1TextArea.setText(beautifulJson1);
            file2TextArea.setText(beautifulJson2);
        }

        // 내용 가져오기
        String content1 = file1TextArea.getText();
        String content2 = file2TextArea.getText();

        List<String> lines1 = Arrays.asList(content1.split("\n"));
        List<String> lines2 = Arrays.asList(content2.split("\n"));

        // 오름차순 정렬
        if (ascending) {
            lines1.sort(String::compareTo);
            lines2.sort(String::compareTo);
            file1TextArea.setText(String.join("\n", lines1));
            file2TextArea.setText(String.join("\n", lines2));
        }

        // 차이점 강조 표시
        errorLines = new java.util.ArrayList<>(); // 오류 라인 저장
        int maxLength = Math.max(lines1.size(), lines2.size());
        for (int i = 0; i < maxLength; i++) {
            String line1 = (i < lines1.size()) ? lines1.get(i) : "";
            String line2 = (i < lines2.size()) ? lines2.get(i) : "";

            if (!line1.equals(line2)) {
                errorLines.add(i); // 오류 라인 추가
                highlightDifferencesInLine(file1TextArea, line1, file2TextArea, line2, i);
            }
        }

        for (Integer errorLine : errorLines) {
            // 노란색 배경으로 라인 강조
            file1TextArea.getHighlighter().addHighlight(file1TextArea.getLineStartOffset(errorLine),
                    file1TextArea.getLineStartOffset(errorLine + 1),
                    new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
            file2TextArea.getHighlighter().addHighlight(file2TextArea.getLineStartOffset(errorLine),
                    file2TextArea.getLineStartOffset(errorLine + 1),
                    new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
        }
        markScrollbar(file1TextArea, errorLines);
        markScrollbar(file2TextArea, errorLines);
    }

    private void highlightDifferencesInLine(JTextArea file1TextArea, String line1, JTextArea file2TextArea, String line2, int lineIndex) {
        try {
            int start1 = file1TextArea.getLineStartOffset(lineIndex);
            int start2 = file2TextArea.getLineStartOffset(lineIndex);

            int minLength = Math.min(line1.length(), line2.length());
            for (int j = 0; j < minLength; j++) {
                if (line1.charAt(j) != line2.charAt(j)) {
                    file1TextArea.getHighlighter().addHighlight(start1 + j, start1 + j + 1, new DefaultHighlighter.DefaultHighlightPainter(Color.BLUE));
                    file2TextArea.getHighlighter().addHighlight(start2 + j, start2 + j + 1, new DefaultHighlighter.DefaultHighlightPainter(Color.BLUE));
                }
            }

            // 길이가 다른 경우 처리
            if (line1.length() != line2.length()) {
                if (line1.length() > line2.length()) {
                    for (int j = minLength; j < line1.length(); j++) {
                        file1TextArea.getHighlighter().addHighlight(start1 + j, start1 + j + 1, new DefaultHighlighter.DefaultHighlightPainter(Color.BLUE));
                    }
                } else {
                    for (int j = minLength; j < line2.length(); j++) {
                        file2TextArea.getHighlighter().addHighlight(start2 + j, start2 + j + 1, new DefaultHighlighter.DefaultHighlightPainter(Color.BLUE));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void markScrollbar(JTextArea textArea, List<Integer> errorLines) {
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, textArea);
        if (scrollPane != null) {
            JPanel markerPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(Color.RED);
                    int lineHeight = textArea.getFontMetrics(textArea.getFont()).getHeight();
                    for (Integer line : errorLines) {
                        int yPosition = line * lineHeight;
                        g.fillRect(getWidth() - 5, yPosition, 5, lineHeight); // Draw marker
                    }
                }
            };
            JScrollBar verticalScrollbar = scrollPane.getVerticalScrollBar();
            verticalScrollbar.add(markerPanel);
        }
    }
}
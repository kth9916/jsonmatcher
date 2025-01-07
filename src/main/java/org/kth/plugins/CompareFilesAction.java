package org.kth.plugins;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.kth.entity.JsonProcessor;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CompareFilesAction implements ToolWindowFactory {
    //
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

        // Key binding for Ctrl + K
        InputMap inputMap1 = file1TextArea.getInputMap(JComponent.WHEN_FOCUSED);
        InputMap inputMap2 = file2TextArea.getInputMap(JComponent.WHEN_FOCUSED);

        ActionMap actionMap1 = file1TextArea.getActionMap();
        ActionMap actionMap2 = file2TextArea.getActionMap();

        // Ctrl + K에 대한 Action 추가
        inputMap1.put(KeyStroke.getKeyStroke("control shift PERIOD"), "insertText");
        inputMap2.put(KeyStroke.getKeyStroke("control shift PERIOD"), "insertText");

        actionMap1.put("insertText", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String textToInsert = file1TextArea.getSelectedText(); // 첫 번째 텍스트 영역에서 선택된 텍스트 가져오기
                if (textToInsert != null) {
                    file2TextArea.replaceSelection(textToInsert); // 두 번째 텍스트 영역에 삽입
                }
            }
        });

        actionMap2.put("insertText", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String textToInsert = file2TextArea.getSelectedText(); // 두 번째 텍스트 영역에서 선택된 텍스트 가져오기
                if (textToInsert != null) {
                    file1TextArea.replaceSelection(textToInsert); // 첫 번째 텍스트 영역에 삽입
                }
            }
        });

        // Main panel to hold both left and right panels
        JPanel mainPanel = new JPanel(new GridLayout(1, 2));
        mainPanel.add(leftPanel);
        mainPanel.add(rightPanel);

        // Compare button
        JButton compareButton = new JButton("Compare");
        compareButton.addActionListener(e -> {
            // ToolWindow 최소화
            toolWindow.hide(null);
            try {
                highlightDifferences(project, file1TextArea, file2TextArea, false, false, beutifyAmisJson);
            } catch (BadLocationException ex) {
                throw new RuntimeException(ex);
            } catch (IOException et) {
                throw new RuntimeException(et);
            }
        });

        // ASC Compare button
        JButton ascCompareButton = new JButton("ASC Compare");
        ascCompareButton.addActionListener(e -> {
            // ToolWindow 최소화
            toolWindow.hide(null);
            try {
                highlightDifferences(project, file1TextArea, file2TextArea, true, false, beutifyAmisJson);
            } catch (BadLocationException ex) {
                throw new RuntimeException(ex);
            }catch (IOException et) {
                throw new RuntimeException(et);
            }
        });

        // DESC Compare button
        JButton descCompareButton = new JButton("DESC Compare");
        descCompareButton.addActionListener(e -> {
            // ToolWindow 최소화
            toolWindow.hide(null);
            try {
                highlightDifferences(project, file1TextArea, file2TextArea, false, true, beutifyAmisJson);
            } catch (BadLocationException ex) {
                throw new RuntimeException(ex);
            }catch (IOException et) {
                throw new RuntimeException(et);
            }
        });

        // Beautify AMIS JSON button
        JButton beautifyAmisJsonButton = new JButton("Beauty Mode ON");
        beautifyAmisJsonButton.addActionListener(e -> {
            beutifyAmisJson = !beutifyAmisJson; //
            beautifyAmisJsonButton.setText(beutifyAmisJson ? "Beauty Mode ON" : "Beauty Mode OFF");
        });

        // Reset JSON button
        JButton resetJsonButton = new JButton("Reset");
        resetJsonButton.addActionListener(e -> {
            file1TextArea.setText("");
            file2TextArea.setText("");
        });

        // Button panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(compareButton);
        buttonPanel.add(ascCompareButton);
        buttonPanel.add(descCompareButton);
        buttonPanel.add(beautifyAmisJsonButton);
        buttonPanel.add(resetJsonButton);

        // Add everything to the main panel
        panel.add(mainPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Tool Window에 콘텐츠 추가
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "File Compare", false);
        toolWindow.getContentManager().addContent(content);
    }

    private void highlightDifferences(
            Project project,
            JTextArea file1TextArea,
            JTextArea file2TextArea,
            boolean ascending,
            boolean descending,
            boolean beautifyAmisJson
    ) throws BadLocationException, IOException {
        //
        String content1 = file1TextArea.getText();
        String content2 = file2TextArea.getText();

        if (beautifyAmisJson) {
            JsonProcessor processor = new JsonProcessor();
            content1 = processor.processJson(content1);
            content2 = processor.processJson(content2);
        }

        List<String> lines1 = Arrays.asList(content1.split("\n"));
        List<String> lines2 = Arrays.asList(content2.split("\n"));

        // 오름차순 정렬
        if (ascending) {
            lines1.sort(String::compareTo);
            lines2.sort(String::compareTo);
            content1 = String.join("\n", lines1);
            content2 = String.join("\n", lines2);
        }

        if (descending) {
            lines1.sort(Collections.reverseOrder());
            lines2.sort(Collections.reverseOrder());
            content1 = String.join("\n", lines1);
            content2 = String.join("\n", lines2);
        }

        // 가상 파일 생성
        VirtualFile file1 = createVirtualFile("File1.txt", content1, project);
        VirtualFile file2 = createVirtualFile("File2.txt", content2, project);

        // DiffManager를 통해 비교 요청
        DiffManager.getInstance().showDiff(project, DiffRequestFactory.getInstance().createFromFiles(project, file1, file2));
    }

    // 가상 파일 생성 메서드
    private VirtualFile createVirtualFile(String name, String content, Project project) throws IOException {
        // 임시 디렉터리 생성
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File tempFile = new File(tempDir, name);

        // 파일에 내용 쓰기
        Files.write(tempFile.toPath(), content.getBytes());

        // LocalFileSystem을 통해 VirtualFile 생성
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);

        if (virtualFile != null) {
            // PsiFile로 변환 (선택 사항)
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        }

        return virtualFile;
    }
}
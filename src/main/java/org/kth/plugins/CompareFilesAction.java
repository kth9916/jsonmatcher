package org.kth.plugins;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.DiffRequestFactoryImpl;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.editor.ChainDiffVirtualFile;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    private DiffRequest currentDiffRequest = null;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Left panel for File 1
        JPanel leftPanel = new JPanel(new BorderLayout());
        JTextArea file1TextArea = new JTextArea(10, 20);
        file1TextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane file1ScrollPane = new JScrollPane(file1TextArea);
        file1ScrollPane.setBorder(BorderFactory.createTitledBorder("File 1"));
        leftPanel.add(file1ScrollPane, BorderLayout.CENTER);

        // Right panel for File 2
        JPanel rightPanel = new JPanel(new BorderLayout());
        JTextArea file2TextArea = new JTextArea(10, 20);
        file2TextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane file2ScrollPane = new JScrollPane(file2TextArea);
        file2ScrollPane.setBorder(BorderFactory.createTitledBorder("File 2"));
        rightPanel.add(file2ScrollPane, BorderLayout.CENTER);

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
        String file1Name = "File1.txt";
        String file2Name = "File2.txt";
        String diffFileName = "diff";

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
            file1Name = "AscFile1.txt";
            file2Name = "AscFile2.txt";
            diffFileName = "AscDiff";
        }

        if (descending) {
            lines1.sort(Collections.reverseOrder());
            lines2.sort(Collections.reverseOrder());
            content1 = String.join("\n", lines1);
            content2 = String.join("\n", lines2);
            file1Name = "DescFile1.txt";
            file2Name = "DescFile2.txt";
            diffFileName = "DescDiff";
        }

        // 가상 파일 생성
        VirtualFile file1 = createVirtualFile(file1Name, content1, project);
        VirtualFile file2 = createVirtualFile(file2Name, content2, project);

        /// 이전 Diff를 닫기 위해 파일을 찾고 닫기
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        VirtualFile[] openFiles = editorManager.getOpenFiles();

        for (VirtualFile openFile : openFiles) {
            // Diff 파일이 열려 있는 경우 닫기
            if(openFile instanceof ChainDiffVirtualFile) {
                ChainDiffVirtualFile chainDiffFile = (ChainDiffVirtualFile) openFile;
                List<? extends DiffRequestProducer> requests = chainDiffFile.getChain().getRequests();
                String finalFile1Name = file1Name;
                String finalFile2Name = file2Name;
                requests.forEach(diffRequestProducer -> {
                    if(diffRequestProducer.getName().contains(finalFile1Name + " - " + finalFile2Name)){
                        editorManager.closeFile(openFile);
                    }
                });
            }
        }

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
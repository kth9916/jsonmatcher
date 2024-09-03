package org.kth.plugins;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.kth.action.GeneratorAction;
import org.kth.entity.TestGenerationTask;

import java.util.ArrayList;
import java.util.List;

public class AllControllerTestGeneratorAction extends AnAction {
    //
    private final GeneratorAction generatorAction;

    public AllControllerTestGeneratorAction() {
        this.generatorAction = new GeneratorAction();
    }

    public AllControllerTestGeneratorAction(GeneratorAction generatorAction) {
        //
        this.generatorAction = generatorAction;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }

        VirtualFile currentFileOrDirectory = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (currentFileOrDirectory == null) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }

        // 디렉토리라면 그 안에 Controller 클래스를 검사
        if (currentFileOrDirectory.isDirectory()) {
            boolean hasControllerClass = containsControllerClass(project, currentFileOrDirectory);
            event.getPresentation().setEnabledAndVisible(hasControllerClass);
        } else if (currentFileOrDirectory.getFileType().getDefaultExtension().equals("java")) {
            // 파일이라면 그 파일이 Controller 클래스인지 검사
            PsiFile psiFile = PsiManager.getInstance(project).findFile(currentFileOrDirectory);
            if (psiFile instanceof PsiJavaFile javaFile) {
                PsiClass[] classes = javaFile.getClasses();
                for (PsiClass psiClass : classes) {
                    if (psiClass.getName() != null && psiClass.getName().endsWith("Controller")) {
                        event.getPresentation().setEnabledAndVisible(true);
                        return;
                    }
                }
            }
            event.getPresentation().setEnabledAndVisible(false);
        } else {
            event.getPresentation().setEnabledAndVisible(false);
        }
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        VirtualFile currentFileOrDirectory = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (currentFileOrDirectory == null) return;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Tests", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false); // 진행 상황을 표시하려면 false로 설정
                List<TestGenerationTask> tasks = new ArrayList<>();

                if (currentFileOrDirectory.isDirectory()) {
                    collectTasksFromDirectory(project, currentFileOrDirectory, tasks);
                } else if (currentFileOrDirectory.getFileType().getDefaultExtension().equals("java")) {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(currentFileOrDirectory);
                    if (psiFile instanceof PsiJavaFile javaFile) {
                        collectTasksFromJavaFile(project, javaFile, tasks);
                    }
                }

                // 진행 상황 업데이트 및 작업 실행
                int totalTasks = tasks.size();
                for (int i = 0; i < totalTasks; i++) {
                    if (indicator.isCanceled()) {
                        break;
                    }
                    TestGenerationTask task = tasks.get(i);
                    generatorAction.createTestClassForMethod(project, task.getDirectoryName(), task.getMethod());
                    indicator.setFraction((i + 1) / (double) totalTasks); // 진행 상황 업데이트
                }
            }
        });
    }

    private boolean containsControllerClass(Project project, VirtualFile directory) {
        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                if (containsControllerClass(project, file)) {
                    return true; // 하위 디렉토리에 Controller 클래스가 있으면 바로 true 반환
                }
            } else if (file.getFileType().getDefaultExtension().equals("java")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile instanceof PsiJavaFile javaFile) {
                    PsiClass[] classes = javaFile.getClasses();
                    for (PsiClass psiClass : classes) {
                        if (psiClass.getName() != null && psiClass.getName().endsWith("Controller")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false; // 해당 디렉토리나 하위 디렉토리에 Controller 클래스가 없음
    }

    private void collectTasksFromDirectory(Project project, VirtualFile directory, List<TestGenerationTask> tasks) {
        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                collectTasksFromDirectory(project, file, tasks);
            } else if (file.getFileType().getDefaultExtension().equals("java")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile instanceof PsiJavaFile javaFile) {
                    collectTasksFromJavaFile(project, javaFile, tasks);
                }
            }
        }
    }

    private void collectTasksFromJavaFile(Project project, PsiJavaFile javaFile, List<TestGenerationTask> tasks) {
        PsiClass[] classes = javaFile.getClasses();

        for (PsiClass psiClass : classes) {
            if (psiClass.getName() == null) continue;

            String controllerName = psiClass.getName();
            if (!controllerName.endsWith("Controller")) continue;

            String directoryName = controllerName.replace("Controller", "").toLowerCase();
            List<PsiMethod> postMappingMethods = PsiTreeUtil.findChildrenOfType(psiClass, PsiMethod.class).stream().toList();

            for (PsiMethod method : postMappingMethods) {
                tasks.add(new TestGenerationTask(directoryName, method));
            }
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}

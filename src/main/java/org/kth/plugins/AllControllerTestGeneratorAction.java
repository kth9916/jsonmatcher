package org.kth.plugins;

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


//    @Override
//    public void actionPerformed(@NotNull AnActionEvent event) {
//        Project project = event.getProject();
//        if (project == null) return;
//
//        // 현재 열려 있는 파일의 디렉토리를 기준으로 작업
//        VirtualFile currentFileOrDirectory = event.getData(CommonDataKeys.VIRTUAL_FILE);
//        if (currentFileOrDirectory == null) return;
//
//        // 하위 디렉토리와 파일들을 순회
//        if (currentFileOrDirectory.isDirectory()) {
//            // 디렉토리라면 기존 로직대로 하위 디렉토리와 파일들을 순회
//            processDirectory(project, currentFileOrDirectory);
//        } else if (currentFileOrDirectory.getFileType().getDefaultExtension().equals("java")) {
//            // 파일이라면 해당 파일이 Controller 클래스인지 확인하고 처리
//            PsiFile psiFile = PsiManager.getInstance(project).findFile(currentFileOrDirectory);
//            if (psiFile instanceof PsiJavaFile javaFile) {
//                processJavaFile(project, javaFile);
//            }
//        }
//    }

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

//    private void processDirectory(Project project, VirtualFile directory) {
//        for (VirtualFile file : directory.getChildren()) {
//            if (file.isDirectory()) {
//                // 재귀적으로 하위 디렉토리 처리
//                processDirectory(project, file);
//            } else if (file.getFileType().getDefaultExtension().equals("java")) {
//                // 자바 파일인 경우 PsiJavaFile로 변환하여 처리
//                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
//                if (psiFile instanceof PsiJavaFile javaFile) {
//                    processJavaFile(project, javaFile);
//                }
//            }
//        }
//    }
//
//    private void processJavaFile(Project project, PsiJavaFile javaFile) {
//        PsiClass[] classes = javaFile.getClasses();
//
//        for (PsiClass psiClass : classes) {
//            if (psiClass.getName() == null) continue;
//
//            String controllerName = psiClass.getName();
//            if (!controllerName.endsWith("Controller")) continue;
//
//            // 컨트롤러 이름을 소문자로 변환하여 디렉토리 이름 생성
//            String directoryName = controllerName.replace("Controller", "").toLowerCase();
//
//            // 메서드를 찾아서 `@PostMapping`만 선택
//            List<PsiMethod> postMappingMethods = PsiTreeUtil.findChildrenOfType(psiClass, PsiMethod.class).stream()
//                    .toList();
//
//            // 각 메서드에 대해 테스트 클래스 생성
//            for (PsiMethod method : postMappingMethods) {
//                generatorAction.createTestClassForMethod(project, directoryName, method);
//            }
//        }
//    }

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
}

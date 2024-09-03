package org.kth.plugins;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.kth.action.GeneratorAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ControllerTestGeneratorAction extends AnAction {
    private static final Logger log = LoggerFactory.getLogger(ControllerTestGeneratorAction.class);
    private final GeneratorAction generatorAction;

    //
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    public ControllerTestGeneratorAction() {
        this.generatorAction = new GeneratorAction();
    }

    public ControllerTestGeneratorAction(GeneratorAction generatorAction) {
        // 기본 생성자
        this.generatorAction = generatorAction;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }

        PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile javaFile)) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }

        PsiClass[] classes = javaFile.getClasses();
        for (PsiClass psiClass : classes) {
            String className = psiClass.getName();
            if (className != null && className.endsWith("Controller")) {
                event.getPresentation().setEnabledAndVisible(true);
                return;
            }
        }

        event.getPresentation().setEnabledAndVisible(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        // 현재 열려 있는 파일을 얻어옴
        PsiFile psiFile = event.getDataContext().getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile javaFile)) return;

        PsiClass[] classes = javaFile.getClasses();

        for (PsiClass psiClass : classes) {
            if (psiClass.getName() == null) continue;

            String controllerName = psiClass.getName();
            if (!controllerName.endsWith("Controller")) continue;

            // 컨트롤러 이름을 소문자로 변환하여 디렉토리 이름 생성
            String directoryName = controllerName.replace("Controller", "").toLowerCase();

            // 메서드를 찾아서 `@PostMapping`만 선택
            List<PsiMethod> postMappingMethods = PsiTreeUtil.findChildrenOfType(psiClass, PsiMethod.class).stream()
                    .toList();

            // 각 메서드에 대해 테스트 클래스 생성
            for (PsiMethod method : postMappingMethods) {
                generatorAction.createTestClassForMethod(project, directoryName, method);
            }
        }
    }
}
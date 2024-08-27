package org.kth.plugins;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ControllerTestGeneratorAction extends AnAction {
    private static final Logger log = LoggerFactory.getLogger(ControllerTestGeneratorAction.class);

    //
    public ControllerTestGeneratorAction() {
        // 기본 생성자
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
//                    .filter(this::methodHasPostMappingWithREnd)
                    .toList();

            // 각 메서드에 대해 테스트 클래스 생성
            for (PsiMethod method : postMappingMethods) {
                createTestClassForMethod(project, directoryName, method);
            }
        }
    }

    private boolean methodHasPostMappingWithREnd(PsiMethod method) {
        PsiAnnotation annotation = method.getAnnotation("org.springframework.web.bind.annotation.PostMapping");
        if (annotation != null) {
            PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
            return value != null && value.getText().endsWith("R\"");
        }
        return false;
    }


    private PsiDirectory ensureSubdirectoriesExist(Project project, String packagePath, PsiDirectory baseDirectory) {
        final PsiDirectory[] currentDirectory = {baseDirectory};
        String[] pathParts = packagePath.split("/");

        // 디렉토리 생성 작업을 WriteCommandAction 내에서 수행합니다.
        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (String part : pathParts) {
                assert currentDirectory[0] != null;
                PsiDirectory subDir = currentDirectory[0].findSubdirectory(part);
                if (subDir == null) {
                    try {
                        subDir = currentDirectory[0].createSubdirectory(part);
                    } catch (IncorrectOperationException e) {
                        e.printStackTrace();
                    }
                }
                currentDirectory[0] = subDir; // 최종적으로 업데이트된 디렉토리
            }
        });

        return currentDirectory[0];
    }

    private void createTestClassForMethod(Project project, String directoryName, PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return;

        String controllerPackage = containingClass.getQualifiedName();
        String[] packageParts = controllerPackage.split("\\.");
        String category = "";
        String basePackageAddress = "";
        String basePackageAddressWithDot = "";
        for (int i = 0; i < packageParts.length; i++) {
            if (packageParts[i].equals("rest")) {
                category = packageParts[i - 1];
                basePackageAddress = packageParts[i - 6] + "/" + packageParts[i - 5] + "/" + packageParts[i - 4] + "/" + packageParts[i - 3];
                basePackageAddressWithDot = packageParts[i - 6] + "." + packageParts[i - 5] + "." + packageParts[i - 4] + "." + packageParts[i - 3];
            }
        }


        String postMappingValue = extractPostMappingValue(method);
        String testClassName = postMappingValue + "Test";
        PsiDirectory moduleTestDirectory = findModuleTestDirectory(project);

        if (moduleTestDirectory == null) {
            // 디렉토리가 없다면 로그를 찍고 종료합니다.
            System.out.println("Test directory not found for any boot module.");
            return;
        }

        // 패키지 경로를 설정합니다.
        String packagePath = basePackageAddress + "/comparison/" + category + "/" + directoryName.toLowerCase();
        String packagePathWithDot = basePackageAddressWithDot + ".comparison." + category + "." + directoryName.toLowerCase();

        // 디렉토리를 생성합니다.
        PsiDirectory finalTargetDirectory = ensureSubdirectoriesExist(project, packagePath, moduleTestDirectory);

        if (finalTargetDirectory == null) {
            System.out.println("Failed to create target directory.");
            return;
        }

        String fileName = testClassName + ".java";
        PsiFile existingFile = finalTargetDirectory.findFile(fileName);
        String newClassContent = generateTestClassContent(method, category, basePackageAddressWithDot);

        if (existingFile != null) {
            String existingFileContent = existingFile.getText();

            // 기존 파일과 새로운 내용을 비교합니다.
            if (existingFileContent.equals(newClassContent)) {
                System.out.println("Test class already exists and is identical: " + fileName);
                return; // 내용이 동일하다면 생성하지 않고 종료합니다.
            } else {
                // 내용이 다르면 파일을 업데이트합니다.
                System.out.println("Test class exists but differs, updating file: " + fileName);
                WriteCommandAction.runWriteCommandAction(project, (Computable<Void>) () -> {
                    existingFile.delete(); // 기존 파일 삭제
                    PsiFile updatedTestClassFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, JavaLanguage.INSTANCE, newClassContent);
                    finalTargetDirectory.add(updatedTestClassFile); // 새로운 파일 추가
                    return null;
                });
            }
        } else {
            // 기존 파일이 없으면 새 파일을 생성합니다.
            PsiFile testClassFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, JavaLanguage.INSTANCE, newClassContent);
            WriteCommandAction.runWriteCommandAction(project, (Computable<Void>) () -> {
                finalTargetDirectory.add(testClassFile);
                return null;
            });
        }
    }

    private PsiDirectory findModuleTestDirectory(Project project) {
        ModuleManager moduleManager = ModuleManager.getInstance(project);

        for (Module module : moduleManager.getModules()) {
            if (module.getName().endsWith("boot")) {
                // 모듈의 기본 디렉토리를 가져옵니다.
                VirtualFile moduleBaseDir = ModuleRootManager.getInstance(module).getContentRoots()[0];

                // 'src/test/java' 디렉토리를 찾습니다.
                VirtualFile testSourceDir = moduleBaseDir.findFileByRelativePath("src/test/java");

                if (testSourceDir != null && testSourceDir.isDirectory()) {
                    // PsiDirectory로 변환하여 반환합니다.
                    return PsiManager.getInstance(project).findDirectory(testSourceDir);
                } else {
                    System.out.println("Test directory not found in module: " + module.getName());
                    return null;
                }
            }
        }
        return null;
    }


    private String generateTestClassContent(PsiMethod method, String category, String basePackageAddressWithDot) {
        String methodName = method.getName();
        String postMappingValue = extractPostMappingValue(method);
        // BootApplication 클래스를 동적으로 찾음
        String addComparisonString = basePackageAddressWithDot + ".comparison";
        String bootApplicationClass = findBootApplicationClass(method.getProject(), addComparisonString);
        String[] split = bootApplicationClass.split("\\.");
        String bootClassName = "";
        for (String name : split) {
            if (name.contains("BootApplication")) {
                bootClassName = name;
            }
        }

        return "package " + addComparisonString + "." + category + "." + methodName.toLowerCase() + ";" +
                "\n" +
                "import com.fasterxml.jackson.databind.JsonNode;\n" +
                "import " + addComparisonString + "." + bootClassName + ";\n" +
                "import kr.amc.amis.testlibrary.api.comparison.client.TestServerClient;\n" +
                "import kr.amc.amis.testlibrary.api.comparison.helper.AmcDataHelper;\n" +
                "import kr.amc.amis.testlibrary.api.comparison.helper.CustomAMCDataAssert;\n" +
                "import kr.amc.amis.testlibrary.api.comparison.logging.annotation.ApiComparison;\n" +
                "import lombok.extern.slf4j.Slf4j;\n" +
                "import org.junit.jupiter.api.Assumptions;\n" +
                "import org.junit.jupiter.api.TestInstance;\n" +
                "import org.junit.jupiter.params.ParameterizedTest;\n" +
                "import org.junit.jupiter.params.provider.Arguments;\n" +
                "import org.junit.jupiter.params.provider.MethodSource;\n" +
                "import org.springframework.beans.factory.annotation.Autowired;\n" +
                "import org.springframework.boot.test.context.SpringBootTest;\n" +
                "\n" +
                "import java.io.IOException;\n" +
                "import java.util.ArrayList;\n" +
                "import java.util.List;\n" +
                "import java.util.stream.Stream;\n" +
                "\n" +
                "@TestInstance(TestInstance.Lifecycle.PER_CLASS)\n" +
                "@SpringBootTest(classes = " + bootClassName + ".class,\n" +
                "        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)\n" +
                "@Slf4j\n" +
                "@ApiComparison(serviceId = \"" + postMappingValue + "\")\n" +
                "public class " + postMappingValue + "Test {\n" +
                "\n" +
                "    @Autowired\n" +
                "    private TestServerClient testServerClient;\n" +
                "\n" +
                "    @ParameterizedTest(name = \"mongoId: {0}  => {1} API TEST\")\n" +
                "    @MethodSource(\"amcDataFromDb\")\n" +
                "    public void apiUnitTest(String mongoId, String svcId, String requestAmcData, String expectedAmcData, String modernResult) throws IOException {\n" +
                "        log.info(\"mongoId : \" + mongoId);\n" +
                "        log.info(\"svcId : \" + svcId);\n" +
                "\n" +
                "        CustomAMCDataAssert.assertThat(modernResult).isEqualToWithDetail(expectedAmcData);\n" +
                "\n" +
                "        log.info(\"requestAmcData\\n\" + requestAmcData);\n" +
                "        log.info(\"expectedAmcData\\n\" + expectedAmcData);\n" +
                "        log.info(\"modernResult\\n\" + modernResult);\n" +
                "    }\n" +
                "\n" +
                "    public Stream<Arguments> amcDataFromDb() {\n" +
                "        List<Arguments> argumentsList = new ArrayList<>();\n" +
                "        List<JsonNode> amcDataCalls = AmcDataHelper.loadAmcDataCollectionsForUnit(\"" + postMappingValue + "\");\n" +
                "        Assumptions.assumeFalse(amcDataCalls.isEmpty(), \"No Test data\");\n" +
                "\n" +
                "        for (JsonNode amcDataCall : amcDataCalls) {\n" +
                "            String svcId = amcDataCall.get(\"svcId\").asText();\n" +
                "            String requestAmcData = amcDataCall.get(\"requestAmcData\").asText();\n" +
                "            String modernResult = testServerClient.callSvcId(svcId, requestAmcData);\n" +
                "            argumentsList.add(Arguments.of(amcDataCall.get(\"_id\").toString(), amcDataCall.get(\"svcId\").asText(), amcDataCall.get(\"requestAmcData\").asText(), amcDataCall.get(\"responseAmcData\").asText(), modernResult));\n" +
                "        }\n" +
                "\n" +
                "        return argumentsList.stream();\n" +
                "    }\n" +
                "}";
    }

    private void createPackageIfNotExists(String packageName, Project project) {
        // IntelliJ API를 사용하여 패키지를 확인하고, 존재하지 않으면 생성하는 로직을 작성합니다.
        PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
        if (psiPackage == null) {
            PsiDirectory baseDir = PsiManager.getInstance(project).findDirectory(project.getBaseDir());
            PsiDirectory newPackageDir = baseDir;
            for (String part : packageName.split("\\.")) {
                PsiDirectory subdir = newPackageDir.findSubdirectory(part);
                if (subdir == null) {
                    subdir = newPackageDir.createSubdirectory(part);
                }
                newPackageDir = subdir;
            }
        }
    }

    private String extractPostMappingValue(PsiMethod method) {
        PsiAnnotation annotation = method.getAnnotation("org.springframework.web.bind.annotation.PostMapping");
        if (annotation != null) {
            PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
            if (value != null) {
                return value.getText().replace("\"", "");
            }
        }
        return "unknown";
    }

    private String findBootApplicationClass(Project project, String basePackageAddressWithDot) {
        // 특정 패키지를 기준으로 검색 시작
        PsiPackage basePackage = JavaPsiFacade.getInstance(project).findPackage(basePackageAddressWithDot);
        if (basePackage != null) {
            return findBootApplicationClassInPackage(basePackage);
        }
        return null;
    }

    private String findBootApplicationClassInPackage(PsiPackage psiPackage) {
        for (PsiClass psiClass : psiPackage.getClasses()) {
            // @SpringBootApplication 어노테이션이 있는지 확인
            PsiAnnotation springBootAppAnnotation = psiClass.getAnnotation("org.springframework.boot.autoconfigure.SpringBootApplication");
            if (springBootAppAnnotation != null) {
                return psiClass.getQualifiedName();
            }
        }

        // 하위 패키지에 대해서도 재귀적으로 탐색
        for (PsiPackage subPackage : psiPackage.getSubPackages()) {
            String result = findBootApplicationClassInPackage(subPackage);
            if (result != null) {
                return result;
            }
        }

        return null;
    }
}
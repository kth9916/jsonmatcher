package org.kth.entity;

import com.intellij.psi.PsiMethod;

public class TestGenerationTask {
    private final String directoryName;
    private final PsiMethod method;

    public TestGenerationTask(String directoryName, PsiMethod method) {
        this.directoryName = directoryName;
        this.method = method;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    public PsiMethod getMethod() {
        return method;
    }
}
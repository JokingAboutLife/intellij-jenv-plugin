package com.github.jokingaboutlife.jenv.service;

import com.github.jokingaboutlife.jenv.constant.JdkExistsType;
import com.github.jokingaboutlife.jenv.dialog.JdkRenameDialog;
import com.github.jokingaboutlife.jenv.model.JenvJdkModel;
import com.github.jokingaboutlife.jenv.model.JenvRenameModel;
import com.github.jokingaboutlife.jenv.util.JenvUtils;
import com.github.jokingaboutlife.jenv.util.JenvVersionParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class JenvJdkTableService {

    private final List<JenvJdkModel> myJenvJdks = new LinkedList<>();
    private final List<JenvJdkModel> myIdeaJdks = new LinkedList<>();

    public static JenvJdkTableService getInstance() {
        return ApplicationManager.getApplication().getService(JenvJdkTableService.class);
    }

    public @NotNull List<JenvJdkModel> getAllJenvJdks() {
        return myJenvJdks;
    }

    public @NotNull List<JenvJdkModel> getAllIdeaJdks() {
        return myIdeaJdks;
    }

    public @NotNull List<JenvJdkModel> getJdksInIdeaAndInJenv() {
        List<JenvJdkModel> jenvJdks = new LinkedList<>();
        for (JenvJdkModel myJenvJdk : myIdeaJdks) {
            if (JenvUtils.checkIsIdeaAndIsJenv(myJenvJdk)) {
                jenvJdks.add(myJenvJdk);
            }
        }
        return jenvJdks;
    }

    public @NotNull List<JenvJdkModel> getJdksInIdeaAndNotInJenv() {
        List<JenvJdkModel> otherJdks = new LinkedList<>();
        for (JenvJdkModel myJenvJdk : myIdeaJdks) {
            if (JenvUtils.checkIsIdeaAndNotJenv(myJenvJdk)) {
                otherJdks.add(myJenvJdk);
            }
        }
        return otherJdks;
    }

    public JenvJdkModel findJenvJdkByName(String jdkName) {
        JenvJdkModel jenvJdkModel = null;
        for (JenvJdkModel myIdeaJdk : myIdeaJdks) {
            if (myIdeaJdk.getName().equals(jdkName)) {
                jenvJdkModel = myIdeaJdk;
                break;
            }
        }
        return jenvJdkModel;
    }

    @NotNull
    private JenvJdkModel createJenvJdkModel(Sdk jdk) {
        JenvJdkModel jenvJdkModel = new JenvJdkModel();
        jenvJdkModel.setName(jdk.getName());
        String versionString = jdk.getVersionString();
        jenvJdkModel.setVersion(JenvVersionParser.tryParse(versionString));
        JdkExistsType jdkExistsType = getIdeaJdkExistsType(jdk, jenvJdkModel.getMajorVersion());
        jenvJdkModel.setExistsType(jdkExistsType);
        jenvJdkModel.setHomePath(jdk.getHomePath());
        jenvJdkModel.setIdeaJdkInfo(jdk);
        return jenvJdkModel;
    }

    private JdkExistsType getIdeaJdkExistsType(Sdk jdk, String majorVersion) {
        JdkExistsType jdkExistsType = JdkExistsType.OnlyInIDEA;
        for (JenvJdkModel myJenvSdk : myJenvJdks) {
            boolean nameMatch = false;
            boolean majorVersionMatch = false;
            boolean homePathMatch = false;
            if (myJenvSdk.getName().equals(jdk.getName())) {
                nameMatch = true;
            }
            if (myJenvSdk.getMajorVersion().equals(majorVersion)) {
                majorVersionMatch = true;
            }
            if (myJenvSdk.getHomePath().equals(jdk.getHomePath())) {
                homePathMatch = true;
            }
            if (myJenvSdk.getCanonicalPath() != null && myJenvSdk.getCanonicalPath().equals(jdk.getHomePath())) {
                homePathMatch = true;
            }
            if (majorVersionMatch) {
                if (jdkExistsType.equals(JdkExistsType.OnlyInIDEA)) {
                    jdkExistsType = JdkExistsType.OnlyMajorVersionMatch;
                }
                if (homePathMatch) {
                    if (nameMatch) {
                        jdkExistsType = JdkExistsType.Both;
                        break;
                    } else {
                        jdkExistsType = JdkExistsType.OnlyNameNotMatch;
                    }
                }
            }
        }
        return jdkExistsType;
    }

    @Deprecated
    private JdkExistsType getJenvJdkExistsType(JenvJdkModel jenvJdkModel) {
        JdkExistsType jdkExistsType = JdkExistsType.OnlyInJEnv;
        for (JenvJdkModel myIdeaJdk : myIdeaJdks) {
            boolean nameMatch = false;
            boolean majorVersionMatch = false;
            boolean homePathMatch = false;
            if (myIdeaJdk.getName().equals(jenvJdkModel.getName())) {
                nameMatch = true;
            }
            if (myIdeaJdk.getMajorVersion().equals(jenvJdkModel.getMajorVersion())) {
                majorVersionMatch = true;
            }
            if (myIdeaJdk.getHomePath().equals(jenvJdkModel.getHomePath())) {
                homePathMatch = true;
            }
            if (jenvJdkModel.getCanonicalPath() != null && myIdeaJdk.getHomePath().equals(jenvJdkModel.getCanonicalPath())) {
                homePathMatch = true;
            }
            if (majorVersionMatch) {
                if (homePathMatch) {
                    jenvJdkModel.setIdeaJdkInfo(myIdeaJdk.getIdeaJdkInfo());
                    if (nameMatch) {
                        jdkExistsType = JdkExistsType.Both;
                        break;
                    } else {
                        jdkExistsType = JdkExistsType.OnlyNameNotMatch;
                    }
                }
            }
        }
        return jdkExistsType;
    }

    public void addToJenvJdks(Sdk jdk) {
        JenvJdkModel jenvJdkModel = createJenvJdkModel(jdk);
        myIdeaJdks.add(jenvJdkModel);
        Collections.sort(myIdeaJdks);
    }

    public void changeJenvJdkName(Sdk jdk, String previousName) {
        JenvJdkModel currentIdeaJdk = null;
        for (JenvJdkModel myIdeaJdk : myIdeaJdks) {
            if (myIdeaJdk.getName().equals(previousName) && myIdeaJdk.getIdeaJdkInfo().equals(jdk)) {
                currentIdeaJdk = myIdeaJdk;
                break;
            }
        }
        if (currentIdeaJdk != null) {
            currentIdeaJdk.setName(jdk.getName());
            JdkExistsType jdkExistsType = getIdeaJdkExistsType(jdk, currentIdeaJdk.getMajorVersion());
            currentIdeaJdk.setExistsType(jdkExistsType);
        }
        Collections.sort(myIdeaJdks);
    }

    public void removeFromJenvJdks(Sdk jdk) {
        String name = jdk.getName();
        for (JenvJdkModel jenvJdkModel : myIdeaJdks) {
            if (name.equals(jenvJdkModel.getName()) && jenvJdkModel.getIdeaJdkInfo().equals(jdk)) {
                myIdeaJdks.remove(jenvJdkModel);
                break;
            }
        }
    }

    public synchronized void refreshJenvJdks() {
        myJenvJdks.clear();
        myIdeaJdks.clear();
        List<File> jenvJdkVersionFiles = JenvUtils.getJenvJdkVersionFiles();
        for (File jenvJdkVersionFile : jenvJdkVersionFiles) {
            JenvJdkModel jenvJdkModel = new JenvJdkModel();
            try {
                jenvJdkModel.setExistsType(JdkExistsType.OnlyInJEnv);
                jenvJdkModel.setName(jenvJdkVersionFile.getName());
                String fullVersion = jenvJdkVersionFile.getName();
                String digitVersion = JenvVersionParser.tryParse(fullVersion);
                jenvJdkModel.setVersion(digitVersion);
                jenvJdkModel.setHomePath(jenvJdkVersionFile.getPath());
                jenvJdkModel.setCanonicalPath(jenvJdkVersionFile.getCanonicalPath());
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            myJenvJdks.add(jenvJdkModel);
        }
        Sdk[] allJdks = ProjectJdkTable.getInstance().getAllJdks();
        Arrays.sort(allJdks, (o1, o2) -> StringUtils.compare(o1.getName(), o2.getName()));
        for (Sdk jdk : allJdks) {
            if (jdk.getSdkType() instanceof JavaSdkType) {
                JenvJdkModel jenvJdkModel = createJenvJdkModel(jdk);
                myIdeaJdks.add(jenvJdkModel);
            }
        }
        Collections.sort(myJenvJdks);
        Collections.sort(myIdeaJdks);
    }

    public void addAllJenvJdksToIdea(Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Add all jEnv JDK") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<JenvRenameModel> renameModelList = new ArrayList<>();
                // analyze jEnv jdk
                analyzeJenvJdk(indicator, renameModelList);
                // rename IDEA SDK dialog
                needToRenameDialog(indicator, project, renameModelList);
                // add jEnv SDK to IDEA
                addJenvJdkToIDEA(indicator);
            }
        });
    }

    private void analyzeJenvJdk(@NotNull ProgressIndicator indicator, List<JenvRenameModel> renameModelList) {
        indicator.setText("Analyze");
        for (int i = 0; i < myIdeaJdks.size(); i++) {
            indicator.setFraction((double) (i + 1) / myIdeaJdks.size());
            JenvJdkModel ideaJdk = myIdeaJdks.get(i);
            if (JenvUtils.checkIsBoth(ideaJdk)) {
                continue;
            }
            String name = ideaJdk.getName();
            String homePath = ideaJdk.getHomePath();
            boolean notJenvButNeedRename = false;
            for (JenvJdkModel jenvJdk : myJenvJdks) {
                String jenvName = jenvJdk.getName();
                String jenvHomePath = jenvJdk.getHomePath();
                String jenvCanonicalPath = jenvJdk.getCanonicalPath();
                if (homePath.equals(jenvHomePath) || homePath.equals(jenvCanonicalPath)) {
                    if (!name.equals(jenvName)) {
                        notJenvButNeedRename = false;
                        JenvRenameModel jenvRenameModel = new JenvRenameModel();
                        jenvRenameModel.setBelongJenv(true);
                        jenvRenameModel.setIdeaSdk(ideaJdk.getIdeaJdkInfo());
                        jenvRenameModel.setChangeName(jenvName);
                        renameModelList.add(jenvRenameModel);
                        break;
                    }
                }
                if (name.equals(jenvName)) {
                    notJenvButNeedRename = true;
                }
            }
            if (notJenvButNeedRename) {
                JenvRenameModel jenvRenameModel = new JenvRenameModel();
                jenvRenameModel.setBelongJenv(false);
                jenvRenameModel.setIdeaSdk(ideaJdk.getIdeaJdkInfo());
                renameModelList.add(jenvRenameModel);
            }
        }
    }

    private static void needToRenameDialog(@NotNull ProgressIndicator indicator, Project project, List<JenvRenameModel> renameModelList) {
        if (!renameModelList.isEmpty()) {
            indicator.setText("Renaming JDK");
            indicator.setFraction(1);
            ApplicationManager.getApplication().invokeAndWait(() -> {
                JdkRenameDialog jdkRenameDialog = new JdkRenameDialog(project, renameModelList);
                jdkRenameDialog.show();
            });
        }
    }

    private static void addJenvJdkToIDEA(@NotNull ProgressIndicator indicator) {
        indicator.setText("Prepare add JDK");
        List<JenvJdkModel> allJenvJdks = JenvJdkTableService.getInstance().getAllJenvJdks();
        List<JenvJdkModel> allIdeaJdks = JenvJdkTableService.getInstance().getAllIdeaJdks();
        for (JenvJdkModel jenvJdk : allJenvJdks) {
            boolean exists = false;
            for (JenvJdkModel ideaJdk : allIdeaJdks) {
                if ((ideaJdk.getHomePath().equals(jenvJdk.getHomePath())
                        || (jenvJdk.getCanonicalPath() != null && ideaJdk.getHomePath().equals(jenvJdk.getCanonicalPath())))
                        || ideaJdk.getName().equals(jenvJdk.getName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    VirtualFile homeDir = VirtualFileManager.getInstance().findFileByNioPath(Path.of(jenvJdk.getHomePath()));
                    if (homeDir != null) {
                        Sdk sdk = SdkConfigurationUtil.setupSdk(ProjectJdkTable.getInstance().getAllJdks(), homeDir, JavaSdk.getInstance(), true, null, jenvJdk.getName());
                        if (sdk != null) {
                            SdkConfigurationUtil.addSdk(sdk);
                        }
                    }
                });
            }
        }
    }
}

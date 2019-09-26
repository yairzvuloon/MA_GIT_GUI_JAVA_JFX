package logicpackage;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CollaborationManager {

    public static void Fetch(Path i_RemotePath, Path i_LocalPath) throws IOException {
        RepositoryManager remoteManager = new RepositoryManager(i_RemotePath, "", false, false, null);
        RepositoryManager localManager = new RepositoryManager(i_LocalPath, "", false, false, null);
        for (Branch branch : localManager.GetAllBranchesList()) {
            if (branch.GetIsRemote()) {
                fetchRemoteBranch(branch, localManager, remoteManager);
            }
        }

    }

    private static void fetchRemoteBranch(Branch i_RemoteBranchInLR, RepositoryManager i_LocalRepository, RepositoryManager i_RemoteRepository) {
        String remoteBranchNameInRR = Paths.get(i_RemoteBranchInLR.GetBranchName()).toFile().getName();
        Branch remoteBranchInRR = i_RemoteRepository.FindBranchByName(remoteBranchNameInRR);
        if (!areCommitsAdjacent(i_RemoteBranchInLR.GetCurrentCommit(), remoteBranchInRR.GetCurrentCommit())) {
            List<Commit> newerCommits = i_RemoteRepository.GetNewerCommitsInBranch(remoteBranchInRR.GetCurrentCommit(), remoteBranchInRR);

        }
    }


    private static Boolean areCommitsAdjacent(Commit i_LocalCommit, Commit i_RemoteCommit) {
        return i_LocalCommit.GetCommitComment()
                .equals(i_RemoteCommit.GetCommitComment())
                && i_LocalCommit.GetCreationDate()
                .equals(i_RemoteCommit.GetCreationDate())
                && i_LocalCommit.GetCreatedBy()
                .equals(i_RemoteCommit.GetCreatedBy());
    }

    public static void CloneRepository(Path i_RemotePath, Path i_LocalPath) throws IOException {
        RepositoryManager remoteRepositoryManager = new RepositoryManager(i_RemotePath, "Administrator", false, false, null);
        new RepositoryManager(i_LocalPath, "Administrator", true, true, null);
        handleClone(remoteRepositoryManager, i_RemotePath, i_LocalPath);
        RepositoryManager localRepository = new RepositoryManager(i_LocalPath, "Administrator", false, false, null);
        localRepository.HandleCheckout(localRepository.GetHeadBranch().GetBranch().GetBranchName());
    }

    private static void handleClone(RepositoryManager i_RepositoryManager, Path i_FromPath, Path i_LocalPath) throws IOException {
        List<Commit> remoteCommitList = i_RepositoryManager.GetSortedAccessibleCommitList();
        List<Commit> clonedCommits = cloneCommits(remoteCommitList, i_LocalPath);
        cloneBranches(i_RepositoryManager, clonedCommits, i_FromPath, i_LocalPath);
        FilesManagement.CreateRemoteReferenceFile(i_FromPath, i_LocalPath);
    }

    private static void cloneBranches(RepositoryManager i_RepositoryManager, List<Commit> i_ClonedCommitsList, Path i_FromPath, Path i_TargetPath) {
        List<Branch> branchesList = i_RepositoryManager.GetAllBranchesList();
        List<Commit> remoteCommitsList = i_RepositoryManager.GetSortedAccessibleCommitList();
        Collections.reverse(remoteCommitsList);

        for (Branch remoteBranch : branchesList) {
            Integer commitIndex = getCommitIndexByBranch(remoteCommitsList, remoteBranch);
            Commit clonedCommit = i_ClonedCommitsList.get(commitIndex);

            String branchName = i_FromPath.toFile().getName() + "\\" + remoteBranch.GetBranchName();
            Branch clonedBranch = new Branch(branchName, clonedCommit, i_TargetPath, true, null, true, null);

            if (remoteBranch.equals(i_RepositoryManager.GetHeadBranch().GetBranch())) {
                HeadBranch headBranch = new HeadBranch(clonedBranch, i_TargetPath, true, null);
                Branch trackingBranch = new Branch(remoteBranch.GetBranchName(), clonedCommit, i_TargetPath, true, null, false, clonedBranch.GetBranchName());
            }
        }
    }

    private static Integer getCommitIndexByBranch(List<Commit> i_CommitList, Branch branch) {
        int i = 0;
        for (i = 0; i < i_CommitList.size(); i++) {
            if (branch.GetCurrentCommit().GetCurrentCommitSHA1().equals(i_CommitList.get(i).GetCurrentCommitSHA1())) {
                break;
            }
        }

        return i;
    }

    private static List<Commit> cloneCommits(List<Commit> remoteCommitList, Path i_LocalPath) throws FileNotFoundException, UnsupportedEncodingException {
        HashMap<Integer, List<Integer>> commitMap = getCommitMap(remoteCommitList);
        List<Commit> clonedCommits = new LinkedList<>();

        for (Commit remoteCommit : remoteCommitList) {
            RootFolder clonedRootFolder = cloneRootFolder(remoteCommit.GetCommitRootFolder(), i_LocalPath);
            Commit clonedCommit = new Commit(clonedRootFolder, remoteCommit.GetCommitComment(), remoteCommit.GetCreatedBy(), null, null, remoteCommit.GetCreationDate());
            clonedCommits.add(clonedCommit);
            FilesManagement.CleanWC(i_LocalPath);
        }

        connectClonedCommits(clonedCommits, commitMap);
        createCommitObjects(clonedCommits, i_LocalPath);

        return clonedCommits;
    }

    private static void createCommitObjects(List<Commit> i_CommitList, Path i_TargetRootPath) {
        Collections.reverse(i_CommitList);
        i_CommitList.forEach(commit -> {
            String sha1 = FilesManagement.CreateCommitDescriptionFile(commit, i_TargetRootPath, true);
            commit.SetCurrentCommitSHA1(sha1);
        });
    }

    private static void connectClonedCommits(List<Commit> io_ClonedCommits, HashMap<Integer, List<Integer>> i_CommitMap) {
        for (int i = 0; i < io_ClonedCommits.size(); i++) {
            List<Integer> prevCommitIndexList = i_CommitMap.get(i);
            List<Commit> prevCommitsList = new LinkedList<>();
            for (Integer index : prevCommitIndexList) {
                prevCommitsList.add(io_ClonedCommits.get(index));
            }

            prevCommitsList = prevCommitsList.isEmpty() ? null : prevCommitsList;
            io_ClonedCommits.get(i).SetPrevCommitsList(prevCommitsList);
        }
    }

    private static HashMap<Integer, List<Integer>> getCommitMap(List<Commit> i_SortedCommitList) {
        HashMap<Integer, List<Integer>> commitMap = new HashMap<>();

        for (int i = 0; i < i_SortedCommitList.size(); i++) {
            Commit currentCommit = i_SortedCommitList.get(i);
            List<Commit> prevCommits = currentCommit.GetPrevCommitsList();
            List<Integer> prevCommitIndexList = new LinkedList<>();

            if (prevCommits != null) {
                for (Commit prevCommit : prevCommits) {
                    prevCommitIndexList.add(i_SortedCommitList.indexOf(prevCommit));
                }
            }

            commitMap.put(i, prevCommitIndexList);

        }

        return commitMap;
    }

    private static RootFolder cloneRootFolder(RootFolder i_RootFolder, Path i_TargetPath) throws FileNotFoundException, UnsupportedEncodingException {
        BlobData clonedFolder = cloneFolder(i_RootFolder.GetBloBDataOfRootFolder(), true, i_TargetPath, i_TargetPath);
        return new RootFolder(clonedFolder, i_TargetPath);
    }

    private static BlobData cloneFolder(BlobData i_RemoteFolder, Boolean i_IsRootFolder, Path i_TargetRootPath, Path i_TargetPath) throws FileNotFoundException, UnsupportedEncodingException {
        String folderName = "";
        if (!i_IsRootFolder) {
            folderName = Paths.get(i_RemoteFolder.GetPath()).toFile().getName();
            FilesManagement.CreateFolder(i_TargetPath, folderName);
        }

        Folder folder = new Folder();
        Path folderPath = Paths.get(i_TargetPath + "\\" + folderName);
        BlobData clonedFolder = new BlobData(i_TargetRootPath, folderPath.toString(), i_RemoteFolder.GetLastChangedBY(), i_RemoteFolder.GetLastChangedTime(), true, "", folder);
        List<BlobData> containedItems = i_RemoteFolder.GetCurrentFolder().GetBlobList();
        for (BlobData blob : containedItems) {
            if (blob.GetIsFolder()) {
                BlobData containedFolder = cloneFolder(blob, false, i_TargetPath, Paths.get(clonedFolder.GetPath()));
                clonedFolder.GetCurrentFolder().AddBlobToList(containedFolder);
            } else {
                BlobData containedElement = cloneSimpleBlob(blob, i_TargetRootPath, Paths.get(clonedFolder.GetPath()));
                clonedFolder.GetCurrentFolder().AddBlobToList(containedElement);
            }
        }

        String sha1 = FilesManagement.CreateFolderDescriptionFile(clonedFolder, i_TargetRootPath, Paths.get(clonedFolder.GetPath()), clonedFolder.GetLastChangedBY(), "", true, null);
        clonedFolder.SetSHA1(sha1);

        return clonedFolder;
    }

    private static BlobData cloneSimpleBlob(BlobData i_Blob, Path i_TargetRootPath, Path i_TargetPath) throws FileNotFoundException, UnsupportedEncodingException {
        String fileName = Paths.get(i_Blob.GetPath()).toFile().getName();
        String lastUpdater = i_Blob.GetLastChangedBY();
        String lastUpdateDate = i_Blob.GetLastChangedTime();
        String content = i_Blob.GetFileContent();
        PrintWriter writer = new PrintWriter(i_TargetPath + "\\" + fileName, "UTF-8");
        writer.println(content);
        writer.close();

        BlobData blob = FilesManagement.CreateSimpleFileDescription(
                i_TargetRootPath,
                Paths.get(i_TargetPath + "\\" + fileName),
                lastUpdater, lastUpdateDate,
                "",
                null,
                true);
        return blob;
    }
}

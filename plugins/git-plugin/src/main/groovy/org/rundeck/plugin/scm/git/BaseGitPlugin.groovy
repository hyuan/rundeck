package org.rundeck.plugin.scm.git

import com.dtolabs.rundeck.core.jobs.JobReference
import com.dtolabs.rundeck.core.plugins.views.Action
import com.dtolabs.rundeck.core.plugins.views.ActionBuilder
import com.dtolabs.rundeck.plugins.scm.JobExportReference
import com.dtolabs.rundeck.plugins.scm.JobFileMapper
import com.dtolabs.rundeck.plugins.scm.JobImportState
import com.dtolabs.rundeck.plugins.scm.JobState
import com.dtolabs.rundeck.plugins.scm.ScmPluginException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit

/**
 * Common features of the import and export plugins
 */
class BaseGitPlugin {
    Git git
    Repository repo
    File workingDir
    String branch
    Map<String, ?> input
    String project
    JobFileMapper mapper
    RawTextComparator COMP = RawTextComparator.DEFAULT
    Map<String, Map> jobStateMap = Collections.synchronizedMap([:])

    def serialize(final JobExportReference job) {
        File outfile = mapper.fileForJob(job)
        if (!outfile.parentFile.exists()) {
            if (!outfile.parentFile.mkdirs()) {
                throw new ScmPluginException(
                        "Cannot create necessary dirs to serialize file to path: ${outfile.absolutePath}"
                )
            }
        }
        outfile.withOutputStream { out ->
            job.jobSerializer.serialize(format, out)
        }
    }

    def serializeAll(final Set<JobExportReference> jobExportReferences) {
        jobExportReferences.each(this.&serialize)
    }

    protected List<Action> actionRefs(String... ids) {
        actions.subMap(Arrays.asList(ids)).values().collect { ActionBuilder.from(it) }
    }

    String debugStatus(final Status status) {
        def smap = [
                conflicting          : status.conflicting,
                added                : status.added,
                changed              : status.changed,
                clean                : status.clean,
                conflictingStageState: status.conflictingStageState,
                ignoredNotInIndex    : status.ignoredNotInIndex,
                missing              : status.missing,
                modified             : status.modified,
                removed              : status.removed,
                uncommittedChanges   : status.uncommittedChanges,
                untracked            : status.untracked,
                untrackedFolders     : status.untrackedFolders,
        ]
        def sb = new StringBuilder()
        smap.each {
            sb << "${it.key}:\n"
            it.value.each {
                sb << "\t${it}\n"
            }
        }
        sb.toString()
    }

    File getLocalFileForJob(final JobReference job) {
        mapper.fileForJob(job)
    }

    String relativePath(File reference) {
        reference.absolutePath.substring(workingDir.getAbsolutePath().length() + 1)
    }

    String relativePath(JobReference reference) {
        relativePath(getLocalFileForJob(reference))
    }
    /**
     * get RevCommit for HEAD rev of the path
     * @return RevCommit or null if HEAD not found (empty git)
     */
    RevCommit getHead() {
        GitUtil.getHead repo
    }

    ObjectId lookupId(RevCommit commit, String path) {
        GitUtil.lookupId repo, commit, path
    }

    byte[] getBytes(ObjectId id) {
        GitUtil.getBytes repo, id
    }

    int diffContent(OutputStream out, byte[] left, File right) {
        GitUtil.diffContent out,  left, right, COMP
    }
    int diffContent(OutputStream out, byte[] left, byte[] right) {
        GitUtil.diffContent out,  left, right, COMP
    }

    RevCommit lastCommit() {
        GitUtil.lastCommit repo, git
    }

    RevCommit lastCommitForPath(String path) {
        GitUtil.lastCommitForPath repo, git, path
    }

    JobState createJobStatus(final Map map) {
        //TODO: include scm status
        return new JobGitState(
                synchState: map['synch'],
                commit: map.commitMeta ? new GitScmCommit(map.commitMeta) : null
        )
    }
    JobImportState createJobImportStatus(final Map map) {
        //TODO: include scm status
        return new JobImportGitState(
                synchState: map['synch'],
                commit: map.commitMeta ? new GitScmCommit(map.commitMeta) : null
        )
    }
}
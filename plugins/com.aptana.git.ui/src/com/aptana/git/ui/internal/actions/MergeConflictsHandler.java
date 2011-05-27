/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.git.ui.internal.actions;

import java.util.Collection;

import org.eclipse.compare.CompareUI;
import org.eclipse.compare.ITypedElement;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.internal.ui.history.FileRevisionTypedElement;
import org.eclipse.team.ui.synchronize.SaveableCompareEditorInput;

import com.aptana.git.core.GitPlugin;
import com.aptana.git.core.model.ChangedFile;
import com.aptana.git.core.model.GitCommit;
import com.aptana.git.core.model.GitRepository;
import com.aptana.git.ui.GitUIPlugin;
import com.aptana.git.ui.internal.history.GitCompareFileRevisionEditorInput;

@SuppressWarnings("restriction")
public class MergeConflictsHandler extends AbstractGitHandler
{

	@Override
	protected Object doExecute(ExecutionEvent event) throws ExecutionException
	{
		Collection<IResource> resources = getSelectedResources();
		if (resources == null || resources.size() != 1)
		{
			return null;
		}
		IResource blah = resources.iterator().next();
		if (blah.getType() != IResource.FILE)
		{
			return null;
		}
		GitRepository repo = getGitRepositoryManager().getAttached(blah.getProject());
		if (repo == null)
		{
			return null;
		}

		String name = repo.getChangedFileForResource(blah).getPath();
		IFile file = (IFile) blah;
		try
		{
			IPath copyPath = file.getFullPath().addFileExtension("conflict"); //$NON-NLS-1$
			IFile copy = ResourcesPlugin.getWorkspace().getRoot().getFile(copyPath);
			if (!copy.exists())
			{
				// We create a copy of the failed merged file with the markers as generated by Git
				file.copy(copyPath, true, new NullProgressMonitor());
				// Then we replace the working file's contents by the contents pre-merge
				final IFileRevision baseFile = GitPlugin.revisionForCommit(
						new GitCommit(repo, ":2"), Path.fromOSString(name)); //$NON-NLS-1$
				IStorage storage = baseFile.getStorage(new NullProgressMonitor());
				file.setContents(storage.getContents(), true, true, new NullProgressMonitor());
				file.setCharset("UTF-8", new NullProgressMonitor()); //$NON-NLS-1$
			}
		}
		catch (CoreException e)
		{
			GitUIPlugin.logError(e);
			return null;
		}
		// Now we use the pre-merge file and compare against the merging version.
		ITypedElement base = SaveableCompareEditorInput.createFileElement(file);
		final IFileRevision nextFile = GitPlugin.revisionForCommit(new GitCommit(repo, ":3"), Path.fromOSString(name)); //$NON-NLS-1$
		final ITypedElement next = new FileRevisionTypedElement(nextFile);
		final GitCompareFileRevisionEditorInput in = new GitCompareFileRevisionEditorInput(base, next, null);
		CompareUI.openCompareEditor(in);

		return null;
	}

	protected boolean calculateEnabled()
	{
		Collection<IResource> resources = getSelectedResources();
		if (resources == null || resources.size() != 1)
		{
			return false;
		}
		IResource blah = resources.iterator().next();
		if (blah.getType() != IResource.FILE)
		{
			return false;
		}
		GitRepository repo = getGitRepositoryManager().getAttached(blah.getProject());
		if (repo == null)
		{
			return false;
		}
		ChangedFile file = repo.getChangedFileForResource(blah);
		if (file == null)
		{
			return false;
		}
		return file.hasUnmergedChanges();
	}

}
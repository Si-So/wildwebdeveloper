/*******************************************************************************
 * Copyright (c) 2018, 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.wildwebdeveloper.debug;

import static org.eclipse.wildwebdeveloper.debug.SelectionUtils.getSelectedFile;
import static org.eclipse.wildwebdeveloper.debug.SelectionUtils.getSelectedProject;
import static org.eclipse.wildwebdeveloper.debug.SelectionUtils.pathOrEmpty;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.wildwebdeveloper.Activator;
import org.eclipse.wildwebdeveloper.debug.chrome.ChromeRunDAPDebugDelegate;
import org.eclipse.wildwebdeveloper.debug.chrome.ChromeRunDebugLaunchShortcut;

public abstract class AbstractRunHTMLDebugTab extends AbstractLaunchConfigurationTab {

	private Text programPathText;
	private Text argumentsText;
	private Text workingDirectoryText;
	protected Composite resComposite;
	private Text urlText;
	protected AbstractDebugAdapterLaunchShortcut shortcut = new ChromeRunDebugLaunchShortcut();
	private Button filePath;
	private ControlDecoration decoration;
	private Button fileRadio;
	private Button urlRadio;

	public AbstractRunHTMLDebugTab() {
	}

	@Override
	public void createControl(Composite parent) {
		resComposite = new Composite(parent, SWT.NONE);
		resComposite.setLayout(new GridLayout(3, false));
		
		fileRadio = createRadioButton(resComposite, Messages.FirefoxDebugTab_File); 
		fileRadio.setToolTipText(Messages.AbstractRunHTMLDebugTab_fileRadioToolTip);
		fileRadio.setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));
		fileRadio.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			urlText.setEnabled(false);
			programPathText.setEnabled(true);
			filePath.setEnabled(true);
			validateProgramPath();
			setDirty(true);
			updateLaunchConfigurationDialog();
		}));
		
		this.programPathText = new Text(resComposite, SWT.BORDER);
		this.programPathText.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));
		decoration = new ControlDecoration(programPathText, SWT.TOP | SWT.LEFT);
		FieldDecoration fieldDecoration = FieldDecorationRegistry.getDefault()
				.getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);
		decoration.setImage(fieldDecoration.getImage());
		this.programPathText.addModifyListener(event -> {
			setDirty(true);
			validateProgramPath();
			updateLaunchConfigurationDialog();
		});
		filePath = new Button(resComposite, SWT.PUSH);
		filePath.setText(Messages.AbstractRunHTMLDebugTab_browse);
		filePath.addSelectionListener(SelectionListener.widgetSelectedAdapter((e) -> {
			FileDialog filePathDialog = new FileDialog(resComposite.getShell());
			filePathDialog.setFilterPath(workingDirectoryText.getText());
			filePathDialog.setText("Select a .html file to debug"); //$NON-NLS-1$
			String path = filePathDialog.open();
			if (path != null) {
				programPathText.setText(path);
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		}));
		
		urlRadio = createRadioButton(resComposite, "URL: ");
		urlRadio.setToolTipText(Messages.RunFirefoxDebugTab_URL_Note);
		urlRadio.setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));
		urlRadio.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
			programPathText.setEnabled(false);
			filePath.setEnabled(false);
			urlText.setEnabled(true);
			decoration.hide();
			setDirty(true);
			updateLaunchConfigurationDialog();
		}));
		urlText = new Text(resComposite, SWT.BORDER);
		GridData urlTextGD = new GridData(SWT.FILL, SWT.DEFAULT, true, false);
		urlTextGD.horizontalSpan = 2;
		urlText.setLayoutData(urlTextGD);
		urlText.addModifyListener(e -> {
			setDirty(true);
			validateProgramPath();
			updateLaunchConfigurationDialog();
		});
		
		new Label(resComposite, SWT.NONE).setText(Messages.RunProgramTab_argument);
		this.argumentsText = new Text(resComposite, SWT.BORDER);
		GridData argsGD = new GridData(SWT.FILL, SWT.DEFAULT, true, false);
		argsGD.horizontalSpan = 2;
		this.argumentsText.setLayoutData(argsGD);
		this.argumentsText.addModifyListener(e -> {
			setDirty(true);
			updateLaunchConfigurationDialog();
		});
		new Label(resComposite, SWT.NONE).setText(Messages.RunProgramTab_workingDirectory);
		this.workingDirectoryText = new Text(resComposite, SWT.BORDER);
		this.workingDirectoryText.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));
		this.workingDirectoryText.addModifyListener(e -> {
			setDirty(true);
			updateLaunchConfigurationDialog();
		});
		Button workingDirectoryButton = new Button(resComposite, SWT.PUSH);
		workingDirectoryButton.setText(Messages.AbstractRunHTMLDebugTab_browse);
		workingDirectoryButton.addSelectionListener(SelectionListener.widgetSelectedAdapter((e) -> {
			DirectoryDialog workingDirectoryDialog = new DirectoryDialog(resComposite.getShell());
			workingDirectoryDialog.setFilterPath(workingDirectoryText.getText());
			workingDirectoryDialog.setText("Select folder to watch for changes"); //$NON-NLS-1$
			String path = workingDirectoryDialog.open();
			if (path != null) {
				workingDirectoryText.setText(path);
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		}));
		setControl(resComposite);
	}

	private void validateProgramPath() {
		setDirty(true);

		String errorMessage = null;
		if (fileRadio.getSelection()) {
			try {
				if (programPathText.getText().length() > 0) {
					File file = new File(VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution(programPathText.getText()));
					if (!file.isFile()) {
						errorMessage = Messages.RunProgramTab_error_unknownFile;
					} else if (!shortcut.canLaunch(file)) {
						errorMessage = "Not a html file"; //$NON-NLS-1$
					} else if (!file.canRead()) {
						errorMessage = Messages.RunProgramTab_error_nonReadableFile;
					}
				}
			} catch (CoreException ex) {
				errorMessage = ex.getMessage();
			}
		} else if (urlRadio.getSelection()) {
			if (urlText.getText().length() > 0) {
				try {
					new URL(urlText.getText());
				} catch (MalformedURLException ex) {
					errorMessage = MessageFormat.format(
							Messages.RunProgramTab_error_malformedUR, 
							ex.getMessage());
				}
			}
		}

		if (errorMessage != null) {
			setErrorMessage(errorMessage);
			decoration.setDescriptionText(errorMessage);
			decoration.show();
		} else {
			setErrorMessage(null);
			decoration.hide();
		}

		updateLaunchConfigurationDialog();
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		// Nothing to do
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			String defaultSelectedFile = pathOrEmpty(getSelectedFile(shortcut::canLaunch));
			this.programPathText
					.setText(configuration.getAttribute(LaunchConstants.PROGRAM, defaultSelectedFile));
			this.argumentsText.setText(configuration.getAttribute(AbstractHTMLDebugDelegate.ARGUMENTS, "")); //$NON-NLS-1$
			this.workingDirectoryText.setText(
					configuration.getAttribute(DebugPlugin.ATTR_WORKING_DIRECTORY, pathOrEmpty(getSelectedProject())));
			this.urlText.setText(configuration.getAttribute(ChromeRunDAPDebugDelegate.URL, "")); //$NON-NLS-1$
			if (urlText.getText().isEmpty()) {
				fileRadio.setSelection(true);
				urlText.setEnabled(false);
			} else {
				programPathText.setEnabled(false);
				filePath.setEnabled(false);
				urlText.setEnabled(true);
				urlRadio.setSelection(true);
				decoration.hide();
			}
		} catch (CoreException e) {
			Activator.getDefault().getLog().log(e.getStatus());
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		String programPath = this.programPathText.getText();
		if (programPathText.isEnabled()) {
			configuration.setAttribute(LaunchConstants.PROGRAM, programPath);
			configuration.setAttribute(ChromeRunDAPDebugDelegate.URL, "");
		} else if (urlText.isEnabled()) {
			configuration.setAttribute(ChromeRunDAPDebugDelegate.URL, urlText.getText());
			configuration.setAttribute(LaunchConstants.PROGRAM, "");
		}

		configuration.setAttribute(AbstractHTMLDebugDelegate.ARGUMENTS, this.argumentsText.getText());
		String workingDirectory = this.workingDirectoryText.getText();
		configuration.setAttribute(DebugPlugin.ATTR_WORKING_DIRECTORY, workingDirectory);
		configuration.setMappedResources(ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(new File(programPath).toURI()));
	}

	@Override
	public String getName() {
		return Messages.RunProgramTab_title;
	}

}

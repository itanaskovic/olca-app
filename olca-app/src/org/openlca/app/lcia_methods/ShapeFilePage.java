package org.openlca.app.lcia_methods;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Hyperlink;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.openlca.app.Messages;
import org.openlca.app.components.FileChooser;
import org.openlca.app.resources.ImageType;
import org.openlca.app.util.Actions;
import org.openlca.app.util.Question;
import org.openlca.app.util.Tables;
import org.openlca.app.util.UI;
import org.openlca.app.util.Viewers;
import org.openlca.app.viewers.table.modify.ModifySupport;
import org.openlca.app.viewers.table.modify.TextCellModifier;
import org.openlca.core.model.ImpactMethod;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows imported shape-files and parameters from these shape-files that can be
 * used in a localised LCIA method.
 */
class ShapeFilePage extends FormPage {

	private Logger log = LoggerFactory.getLogger(getClass());
	private ImpactMethod method;
	private FormToolkit toolkit;
	private Composite body;
	private ShapeFileSection[] sections;
	private ScrolledForm form;

	public ShapeFilePage(ImpactMethodEditor editor) {
		super(editor, "ShapeFilePage", "Shape files (beta)");
		this.method = editor.getModel();
	}

	@Override
	protected void createFormContent(IManagedForm managedForm) {
		form = UI.formHeader(managedForm, "Shape file parameters");
		toolkit = managedForm.getToolkit();
		body = UI.formBody(form, toolkit);
		createFileSection();
		List<String> shapeFiles = ShapeFileUtils.getShapeFiles(method);
		sections = new ShapeFileSection[shapeFiles.size()];
		for (int i = 0; i < shapeFiles.size(); i++)
			sections[i] = new ShapeFileSection(i, shapeFiles.get(i));
		form.reflow(true);
	}

	private void createFileSection() {
		Composite composite = UI.formSection(body, toolkit, "Files");
		createFolderLink(composite);
		UI.formLabel(composite, toolkit, "");
		Button importButton = toolkit.createButton(composite, Messages.Import,
				SWT.NONE);
		importButton.setImage(ImageType.IMPORT_ICON.get());
		importButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				File file = FileChooser.forImport("*.shp");
				if (file != null)
					checkRunImport(file);
			}
		});
	}

	private void createFolderLink(Composite composite) {
		UI.formLabel(composite, toolkit, "Location");
		Hyperlink hyperlink = new Hyperlink(composite, SWT.NONE);
		toolkit.adapt(hyperlink);
		final File folder = ShapeFileUtils.getFolder(method);
		hyperlink.setText(Strings.cut(folder.getAbsolutePath(), 75));
		hyperlink.setToolTipText(folder.getAbsolutePath());
		hyperlink.addHyperlinkListener(new HyperlinkAdapter() {
			@Override
			public void linkActivated(HyperlinkEvent e) {
				try {
					Desktop.getDesktop().open(folder);
				} catch (Exception ex) {
					log.error("failed to open shape-file folder", ex);
				}
			}
		});
	}

	private void checkRunImport(File file) {
		if (!ShapeFileUtils.isValid(file)) {
			org.openlca.app.util.Error.showBox("Invalid file", "The file "
					+ file.getName() + " is not a valid shape file.");
			return;
		}
		if (ShapeFileUtils.alreadyExists(method, file)) {
			org.openlca.app.util.Error
					.showBox(
							"File already exists",
							"A shape "
									+ "file with the given name already exists for this method.");
			return;
		}
		try {
			runImport(file);
		} catch (Exception e) {
			log.error("Failed to import shape file " + file, e);
		}
	}

	private void runImport(File file) throws Exception {
		String shapeFile = ShapeFileUtils.importFile(method, file);
		ShapeFileSection section = new ShapeFileSection(sections.length,
				shapeFile);
		ShapeFileSection[] newSections = new ShapeFileSection[sections.length + 1];
		System.arraycopy(sections, 0, newSections, 0, sections.length);
		newSections[sections.length] = section;
		this.sections = newSections;
		form.reflow(true);
	}

	private class ShapeFileSection {

		private int index;
		private String shapeFile;
		private Section section;
		private ShapeFileParameterTable parameterTable;

		ShapeFileSection(int index, String shapeFile) {
			this.index = index;
			this.shapeFile = shapeFile;
			render();
		}

		private void render() {
			section = UI.section(body, toolkit, "Parameters of " + shapeFile);
			Composite composite = UI.sectionClient(section, toolkit);
			parameterTable = new ShapeFileParameterTable(shapeFile, composite);
			bindActions();
		}

		private void bindActions() {
			Action show = new Action() {
				{
					setToolTipText("Show features");
					setImageDescriptor(ImageType.LCIA_ICON.getDescriptor());
				}

				@Override
				public void run() {
					openFile();
				}
			};
			Action save = Actions.onSave(new Runnable() {
				@Override
				public void run() {
					parameterTable.onSave();
				}
			});
			Action delete = Actions.onRemove(new Runnable() {
				@Override
				public void run() {
					delete();
				}
			});
			Actions.bind(section, show, save, delete);
		}

		private void openFile() {
			File folder = ShapeFileUtils.getFolder(method);
			if (folder == null)
				return;
			try {
				File file = new File(folder, shapeFile + ".shp");
				log.trace("open shape-file in map: {}", file);
				ShapefileDataStore dataStore = new ShapefileDataStore(
						file.toURI().toURL());
				SimpleFeatureCollection source = dataStore.getFeatureSource(
						dataStore.getTypeNames()[0]).getFeatures();
				MapContent mapContent = new MapContent();
				mapContent.setTitle("Features of " + shapeFile);
				Style style = SLD.createSimpleStyle(source.getSchema());
				Layer layer = new FeatureLayer(source, style);
				mapContent.addLayer(layer);
				JMapFrame.showMap(mapContent);
			} catch (Exception e) {
				log.error("Failed to open shape-file", e);
			}
		}

		private void delete() {
			boolean del = Question.ask("Delete " + shapeFile + "?", "Do you "
					+ "really want to delete " + shapeFile + "?");
			if (!del)
				return;
			ShapeFileUtils.deleteFile(method, shapeFile);
			section.dispose();
			ShapeFileSection[] newSections = new ShapeFileSection[sections.length - 1];
			System.arraycopy(sections, 0, newSections, 0, index);
			if ((index + 1) < sections.length)
				System.arraycopy(sections, index + 1, newSections, index,
						newSections.length - index);
			sections = newSections;
			form.reflow(true);
		}
	}

	private class ShapeFileParameterTable {

		private String[] columns = {Messages.Name, Messages.Description};
		private TableViewer viewer;
		private String shapeFile;
		private List<ShapeFileParameter> params;

		ShapeFileParameterTable(String shapeFile, Composite parent) {
			this.shapeFile = shapeFile;
			viewer = Tables.createViewer(parent, columns);
			viewer.setLabelProvider(new ShapeFileParameterLabel());
			Tables.bindColumnWidths(viewer, 0.2, 0.8);
			bindActions();
			ModifySupport<ShapeFileParameter> modifySupport =
					new ModifySupport<>(viewer);
			modifySupport.bind(Messages.Name, new NameModifier());
			modifySupport.bind(Messages.Description, new DescriptionModifier());
			try {
				params = ShapeFileUtils.readParameters(method, shapeFile);
				viewer.setInput(params);
			} catch (Exception e) {
				log.error("failed to read parameteres for shape file " +
						shapeFile, e);
			}
		}

		private void bindActions() {
			Action add = Actions.onAdd(new Runnable() {
				@Override
				public void run() {
					onAdd();
				}
			});
			Action remove = Actions.onRemove(new Runnable() {
				@Override
				public void run() {
					onRemove();
				}
			});
			Actions.bind(viewer, add, remove);
		}

		private void onAdd() {
			ShapeFileParameter p = new ShapeFileParameter();
			p.setName("p_" + (params.size() + 1));
			params = new ArrayList<>(params);
			params.add(p);
			viewer.setInput(params);
		}

		private void onRemove() {
			ShapeFileParameter p = Viewers.getFirstSelected(viewer);
			if (p == null)
				return;
			params = new ArrayList<>(params);
			params.remove(p);
			viewer.setInput(params);
		}

		private void onSave() {
			try {
				ShapeFileUtils.writeParameters(method, shapeFile, params);
			} catch (Exception e) {
				log.error("failed to save parameters for shape file "
						+ shapeFile, e);
			}
		}


		private class NameModifier extends TextCellModifier<ShapeFileParameter> {
			@Override
			protected String getText(ShapeFileParameter param) {
				return param.getName();
			}

			@Override
			protected void setText(ShapeFileParameter param, String text) {
				param.setName(text);
				viewer.refresh();
			}
		}

		private class DescriptionModifier extends TextCellModifier<ShapeFileParameter> {
			@Override
			protected String getText(ShapeFileParameter param) {
				return param.getDescription();
			}

			@Override
			protected void setText(ShapeFileParameter param, String text) {
				param.setDescription(text);
				viewer.refresh();
			}
		}

		private class ShapeFileParameterLabel extends LabelProvider implements
				ITableLabelProvider {

			@Override
			public Image getColumnImage(Object o, int i) {
				if (i == 0)
					return ImageType.FORMULA_ICON.get();
				else
					return null;
			}

			@Override
			public String getColumnText(Object o, int i) {
				if (!(o instanceof ShapeFileParameter))
					return null;
				ShapeFileParameter p = (ShapeFileParameter) o;
				if (i == 0)
					return p.getName();
				else
					return p.getDescription();
			}
		}
	}
}
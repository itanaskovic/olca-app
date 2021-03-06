package org.openlca.app.viewers.combo;

import java.util.Collections;
import java.util.List;

import org.eclipse.swt.widgets.Composite;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.ImpactMethodDao;
import org.openlca.core.model.descriptors.ImpactMethodDescriptor;
import org.openlca.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImpactMethodViewer extends
		AbstractComboViewer<ImpactMethodDescriptor> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	public ImpactMethodViewer(Composite parent) {
		super(parent);
		setInput(new ImpactMethodDescriptor[0]);
	}

	public void setInput(IDatabase db) {
		try {
			ImpactMethodDao dao = new ImpactMethodDao(db);
			List<ImpactMethodDescriptor> list = dao.getDescriptors();
			Collections.sort(list,
					(m1, m2) -> Strings.compare(m1.name, m2.name));
			setInput(list.toArray(new ImpactMethodDescriptor[list.size()]));
		} catch (Exception e) {
			log.error("Loading impact methods failed", e);
		}
	}

	@Override
	public Class<ImpactMethodDescriptor> getType() {
		return ImpactMethodDescriptor.class;
	}

}

package org.openlca.app.editors.graphical.policy;

import org.eclipse.draw2d.Connection;
import org.eclipse.draw2d.ConnectionRouter;
import org.eclipse.draw2d.PolygonDecoration;
import org.eclipse.draw2d.PolylineConnection;
import org.eclipse.gef.Request;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.editpolicies.GraphicalNodeEditPolicy;
import org.eclipse.gef.requests.CreateConnectionRequest;
import org.eclipse.gef.requests.ReconnectRequest;
import org.openlca.app.editors.graphical.command.CommandFactory;
import org.openlca.app.editors.graphical.command.CreateLinkCommand;
import org.openlca.app.editors.graphical.model.ConnectionLink;
import org.openlca.app.editors.graphical.model.ExchangeNode;
import org.openlca.app.editors.graphical.model.ProcessNode;

public class LinkPolicy extends GraphicalNodeEditPolicy {

	private PolylineConnection connection;

	@Override
	protected Connection createDummyConnection(Request req) {
		connection = (PolylineConnection) super.createDummyConnection(req);
		connection.setForegroundColor(ConnectionLink.COLOR);
		if (req instanceof CreateConnectionRequest) {
			CreateLinkCommand command = (CreateLinkCommand) ((CreateConnectionRequest) req)
					.getStartCommand();
			if (command.providerNode != null)
				connection.setTargetDecoration(new PolygonDecoration());
			else if (command.exchangeNode != null)
				connection.setSourceDecoration(new PolygonDecoration());
		} else
			connection.setTargetDecoration(new PolygonDecoration());
		return connection;
	}

	@Override
	protected Command getConnectionCompleteCommand(CreateConnectionRequest req) {
		if (req.getStartCommand() == null)
			return null;
		CreateLinkCommand cmd = (CreateLinkCommand) req.getStartCommand();
		Object model = req.getTargetEditPart().getModel();
		if (!(model instanceof ExchangeNode))
			return null;
		ExchangeNode node = (ExchangeNode) model;

		if (!target.getExchange().isInput())
			cmd.providerNode = targetNode;
		else if (!targetNode.hasIncomingConnection(cmd.flowId))
			cmd.exchangeNode = targetNode;
		req.setStartCommand(cmd);
		return cmd;
	}

	@Override
	protected Command getConnectionCreateCommand(CreateConnectionRequest request) {
		ExchangeNode target = (ExchangeNode) request.getTargetEditPart()
				.getModel();
		ProcessNode targetNode = target.getParent().getParent();
		CreateLinkCommand cmd = null;
		long flowId = target.getExchange().getFlow().getId();
		if (!target.getExchange().isInput()) {
			cmd = CommandFactory.createCreateLinkCommand(flowId);
			cmd.providerNode = targetNode;
			cmd.startedFromSource = true;
			request.setStartCommand(cmd);
		} else if (!targetNode.hasIncomingConnection(flowId)) {
			cmd = CommandFactory.createCreateLinkCommand(flowId);
			cmd.exchangeNode = targetNode;
			cmd.startedFromSource = false;
			request.setStartCommand(cmd);
		}
		return cmd;
	}

	@Override
	protected ConnectionRouter getDummyConnectionRouter(
			CreateConnectionRequest request) {
		return ConnectionRouter.NULL;
	}

	@Override
	protected Command getReconnectSourceCommand(ReconnectRequest request) {
		if (request.getTarget().getModel() instanceof ExchangeNode) {
			ConnectionLink link = (ConnectionLink) request
					.getConnectionEditPart().getModel();
			ExchangeNode source = (ExchangeNode) request.getTarget().getModel();
			ProcessNode sourceNode = source.getParent().getParent();
			return CommandFactory.createReconnectLinkCommand(link, sourceNode,
					link.targetNode);
		}
		return null;
	}

	@Override
	protected Command getReconnectTargetCommand(ReconnectRequest request) {
		if (request.getTarget().getModel() instanceof ExchangeNode) {
			ConnectionLink link = (ConnectionLink) request
					.getConnectionEditPart().getModel();
			ExchangeNode target = (ExchangeNode) request.getTarget().getModel();
			ProcessNode targetNode = target.getParent().getParent();
			long flowId = link.processLink.flowId;
			boolean canConnect = true;
			if (!link.targetNode.equals(targetNode)
					&& targetNode.hasIncomingConnection(flowId))
				canConnect = false;
			if (canConnect)
				return CommandFactory.createReconnectLinkCommand(link,
						link.sourceNode, targetNode);
		}
		return null;
	}

	@Override
	public void eraseSourceFeedback(Request request) {
		if (getHost().getModel() instanceof ExchangeNode) {
			ExchangeNode node = (ExchangeNode) getHost().getModel();
			node.getParent().getParent().getParent().removeHighlighting();
			node.setHighlighted(false);
		}
		super.eraseSourceFeedback(request);
	}

	@Override
	public void showSourceFeedback(Request request) {
		if (getHost().getModel() instanceof ExchangeNode) {
			ExchangeNode node = (ExchangeNode) getHost().getModel();
			node.getParent().getParent().getParent()
					.highlightMatchingExchanges(node);
			node.setHighlighted(true);
		}
		super.showSourceFeedback(request);
	}
}

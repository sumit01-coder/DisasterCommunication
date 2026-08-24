% DisasterComm Mesh Network Simulation
% Simulates 550+ concurrent users in a disaster area communicating via mesh

clear; clc; close all;

%% Parameters
NUM_NODES = 1200;
AREA_SIZE = 1000; % 1000x1000 meters (1 sq km)
COMM_RANGE = 80;  % Effective range in meters (Bluetooth + Wi-Fi Aware)
TTL = 15;          % Maximum hops for a message

%% Generate random node positions
rng(42); % Set seed for reproducibility
x = rand(NUM_NODES, 1) * AREA_SIZE;
y = rand(NUM_NODES, 1) * AREA_SIZE;
positions = [x, y];

%% Construct the Adjacency Matrix
% Connect nodes if they are within the communication range
adj_matrix = zeros(NUM_NODES, NUM_NODES);
for i = 1:NUM_NODES
    for j = i+1:NUM_NODES
        dist = norm(positions(i,:) - positions(j,:));
        if dist <= COMM_RANGE
            adj_matrix(i,j) = 1;
            adj_matrix(j,i) = 1;
        end
    end
end

% Create MATLAB Graph Object
G = graph(adj_matrix);

%% Simulate Message Broadcast
% Find the node closest to the center to act as the sender
center = [AREA_SIZE/2, AREA_SIZE/2];
dists_to_center = sum((positions - center).^2, 2);
[~, source_node] = min(dists_to_center);

% Simulate flooding algorithm up to TTL
reached_nodes = false(NUM_NODES, 1);
reached_nodes(source_node) = true;
current_level = source_node;

for hop = 1:TTL
    next_level = [];
    for i = 1:length(current_level)
        node = current_level(i);
        neighbors = find(adj_matrix(node, :));
        for j = 1:length(neighbors)
            if ~reached_nodes(neighbors(j))
                reached_nodes(neighbors(j)) = true;
                next_level = [next_level, neighbors(j)];
            end
        end
    end
    if isempty(next_level)
        break;
    end
    current_level = next_level;
end

num_reached = sum(reached_nodes);
fprintf('Total Nodes: %d\n', NUM_NODES);
fprintf('Nodes Reached: %d (%.1f%%)\n', num_reached, (num_reached/NUM_NODES)*100);

%% Visualization
figure('Name', 'DisasterComm Mesh Simulation', 'Color', 'w', 'Position', [100 100 800 800]);
hold on;

% Plot Edges
[row, col] = find(adj_matrix);
for i = 1:length(row)
    if row(i) < col(i) % Avoid drawing twice
        plot([x(row(i)), x(col(i))], [y(row(i)), y(col(i))], 'Color', [0.8 0.8 0.8], 'LineWidth', 0.5);
    end
end

% Plot Unreached Nodes
unreached_idx = find(~reached_nodes);
scatter(x(unreached_idx), y(unreached_idx), 20, [0.6 0.6 0.6], 'filled');

% Plot Reached Nodes
reached_idx = find(reached_nodes);
% Remove source node from this list so it can be colored differently
reached_idx(reached_idx == source_node) = []; 
scatter(x(reached_idx), y(reached_idx), 40, [0.3 0.7 0.3], 'filled'); % Green

% Plot Source Node
scatter(x(source_node), y(source_node), 100, [0.9 0.2 0.2], 'filled', 'MarkerEdgeColor', 'k'); % Red

title(sprintf('Mesh Network Simulation (1200 Nodes)\nDelivery via Multi-hop Routing (TTL=%d)', TTL));
xlabel('Meters');
ylabel('Meters');
axis equal;
axis([0 AREA_SIZE 0 AREA_SIZE]);
legend('Connections', 'Unreached Nodes', 'Message Reached', 'Source Node', 'Location', 'bestoutside');
hold off;

#!/bin/bash

set -e 

echo "🚀 Starting Cassandra cluster..."
docker-compose up -d

# Function to check if a specific Cassandra node is ready
check_cassandra_node() {
  local node_name=$1
  local max_attempts=$2
  local attempt=0

  echo "⏳ Waiting for $node_name to be ready..."
  while ! docker exec "$node_name" cqlsh -e "describe cluster" >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_attempts ]; then
      echo "❌ $node_name did not become ready in time."
      return 1
    fi
    echo "⌛ Still waiting for $node_name... (attempt $attempt/$max_attempts)"
    sleep 5
  done

  echo "✅ $node_name is ready!"
  return 0
}

check_cassandra_node cassandra-node1 30 || exit 1
check_cassandra_node cassandra-node2 20 || exit 1
check_cassandra_node cassandra-node3 20 || exit 1

echo "⏳ Waiting for cassandra-web to be ready..."
attempt=0
max_attempts=12
while ! curl -s http://localhost:8083 > /dev/null; do
  attempt=$((attempt + 1))
  if [ $attempt -ge $max_attempts ]; then
    echo "❌ cassandra-web did not become ready in time."
    break
  fi
  echo "⌛ Still waiting for cassandra-web... (attempt $attempt/$max_attempts)"
  sleep 5
done

if [ $attempt -lt $max_attempts ]; then
  echo "✅ cassandra-web is ready! Access it at http://localhost:8083"
fi

echo "📊 Checking cluster status..."
docker exec cassandra-node1 nodetool status

echo "🛠️ Building the Cassandra Seeder application..."
mvn clean package -DskipTests

echo "📜 Initializing schema via SchemaInitializer..."
java -cp target/social-app-1.0-SNAPSHOT.jar com.example.socialapp.SchemaInitializer

echo "🌱 Seeding initial data using Seeder..."
java -jar target/social-app-1.0-SNAPSHOT.jar

echo "📈 Starting write load generator (default: 5 minutes)..."
java -cp target/social-app-1.0-SNAPSHOT.jar com.example.socialapp.WriteLoadGenerator

echo "🎉 Done!"

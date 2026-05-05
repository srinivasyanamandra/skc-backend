#!/bin/bash

# Sri Karthikeya Caterers - Database Setup Script

echo "🔧 Sri Karthikeya Caterers - Database Setup"
echo "==========================================="
echo ""

# Check if PostgreSQL is installed
if ! command -v psql &> /dev/null; then
    echo "❌ PostgreSQL is not installed or not in PATH"
    echo ""
    echo "Install PostgreSQL:"
    echo "  macOS:   brew install postgresql@14"
    echo "  Ubuntu:  sudo apt install postgresql postgresql-contrib"
    echo "  Windows: Download from https://www.postgresql.org/download/"
    exit 1
fi

echo "✅ PostgreSQL is installed"

# Check if PostgreSQL is running
if ! pg_isready -q; then
    echo "❌ PostgreSQL is not running"
    echo ""
    echo "Start PostgreSQL:"
    echo "  macOS:   brew services start postgresql@14"
    echo "  Ubuntu:  sudo systemctl start postgresql"
    echo "  Windows: Start PostgreSQL service from Services"
    exit 1
fi

echo "✅ PostgreSQL is running"
echo ""

# Database name
DB_NAME="sri_karthikeya_caterers"
DB_USER="${DB_USERNAME:-postgres}"

# Check if database exists
if psql -U "$DB_USER" -lqt | cut -d \| -f 1 | grep -qw "$DB_NAME"; then
    echo "✅ Database '$DB_NAME' already exists"
else
    echo "📦 Creating database '$DB_NAME'..."
    createdb -U "$DB_USER" "$DB_NAME"
    
    if [ $? -eq 0 ]; then
        echo "✅ Database '$DB_NAME' created successfully"
    else
        echo "❌ Failed to create database"
        echo ""
        echo "Try manually:"
        echo "  psql -U $DB_USER"
        echo "  CREATE DATABASE $DB_NAME;"
        exit 1
    fi
fi

echo ""
echo "🎉 Database setup complete!"
echo ""
echo "Next steps:"
echo "  1. Configure .env file with your credentials"
echo "  2. Run the application:"
echo "     ./mvnw spring-boot:run -Dspring-boot.run.profiles=local"
echo ""

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  password_md5 TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'customer',
  full_name TEXT NOT NULL,
  city TEXT NOT NULL,
  phone TEXT,
  wallet_balance NUMERIC(10,2) NOT NULL DEFAULT 100.00,
  avatar_url TEXT,
  reset_hash TEXT,
  reset_expires TIMESTAMP,
  reset_attempts INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS shipments (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL REFERENCES users(id),
  tracking_code TEXT UNIQUE,
  recipient_name TEXT NOT NULL,
  recipient_city TEXT NOT NULL,
  recipient_address TEXT NOT NULL,
  weight_kg NUMERIC(8,2) NOT NULL,
  service TEXT NOT NULL,
  price NUMERIC(10,2) NOT NULL,
  status TEXT NOT NULL DEFAULT 'created',
  instructions TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS events (
  id BIGSERIAL PRIMARY KEY,
  shipment_id BIGINT NOT NULL REFERENCES shipments(id),
  status TEXT NOT NULL,
  location TEXT,
  note TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT now()
)

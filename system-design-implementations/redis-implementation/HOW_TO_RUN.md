# How to Run Redis Examples

## Prerequisites

Redis must be installed and running locally.

### macOS Installation
```bash
brew install redis
brew services start redis
```

### Linux Installation
```bash
sudo apt-get install redis-server  # Ubuntu/Debian
sudo systemctl start redis
```

## Step 1: Install Dependencies
```bash
cd system-design-implementations/redis-implementation
pip install -r requirements.txt
```

## Step 2: Run Any Example
```bash
python3 01-basics/01_strings.py
python3 01-basics/02_lists.py
python3 01-basics/03_sets.py
python3 01-basics/04_hashes.py
python3 01-basics/05_sorted_sets.py
```

## What Each Example Does

- **01_strings.py** - Caching, sessions, rate limiting, distributed locks
- **02_lists.py** - Task queues, message queues, activity feeds
- **03_sets.py** - Unique visitors, tagging, social networks, permissions
- **04_hashes.py** - User profiles, shopping carts, feature flags
- **05_sorted_sets.py** - Leaderboards, rankings, time series data

## 📚 Interview Preparation

See **INTERVIEW_GUIDE.md** for:
- System design interview questions using Redis
- Real-world examples (Twitter, Instagram, Uber)
- Performance comparisons vs databases
- Scaling strategies
- Common interview patterns

---

That's it! Each file is independent and self-runnable.

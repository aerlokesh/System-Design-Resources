# Redis Implementation

Learn Redis through hands-on examples covering all major data types and real-world use cases.

## What's Included

- **01_strings.py** - Caching, sessions, rate limiting, locks
- **02_lists.py** - Task queues, message queues, activity feeds
- **03_sets.py** - Unique visitors, tagging, social networks
- **04_hashes.py** - User profiles, shopping carts, feature flags
- **05_sorted_sets.py** - Leaderboards, rankings, time series

**Total: 40+ production-ready examples**

## Quick Start

### 🚀 New: All-in-One Interactive Program

The easiest way to learn Redis! Run all examples from one interactive menu:

```bash
python redis_all_in_one.py
```

### Standard Approach

See **HOW_TO_RUN.md** or **QUICK_START.md** for complete instructions.

```bash
# 1. Install dependencies
pip install -r requirements.txt

# 2. Setup Redis (Docker recommended)
docker run -d --name redis-learning -p 6379:6379 redis:latest

# 3. Run all-in-one program (recommended)
python redis_all_in_one.py

# OR run individual files
python 01-basics/01_strings.py
python 01-basics/02_lists.py
python 01-basics/03_sets.py
python 01-basics/04_hashes.py
python 01-basics/05_sorted_sets.py
```

All files are independent and self-runnable.

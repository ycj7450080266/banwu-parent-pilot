const KEY = 'banwu.tasks'

function getAll() {
  return wx.getStorageSync(KEY) || []
}

function saveAll(tasks) {
  wx.setStorageSync(KEY, tasks)
}

function add(task) {
  const tasks = getAll()
  tasks.unshift(Object.assign({
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    createdAt: Date.now(),
    done: false,
    source: '家长手动补充',
    excerpt: ''
  }, task))
  saveAll(tasks)
}

function update(id, patch) {
  saveAll(getAll().map(item => item.id === id ? Object.assign({}, item, patch) : item))
}

function remove(id) {
  saveAll(getAll().filter(item => item.id !== id))
}

module.exports = { getAll, saveAll, add, update, remove }


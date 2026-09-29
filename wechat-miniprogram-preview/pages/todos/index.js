const repository = require('../../services/taskRepository')

Page({
  data: {
    profile: { childName: '孩子', groupName: '班级群' },
    todayLabel: '',
    filters: [{ key: 'all', label: '全部' }, { key: 'today', label: '今天' }, { key: 'week', label: '本周' }, { key: 'done', label: '已完成' }],
    activeFilter: 'all', tasks: [], visibleTasks: [], pendingCount: 0,
    showForm: false, draftTitle: '', draftDue: '', emptyTitle: '还没有待办'
  },

  onShow() { this.refresh() },
  onPullDownRefresh() { this.refresh(); wx.stopPullDownRefresh() },

  refresh() {
    const profile = getApp().globalData.profile || wx.getStorageSync('banwu.profile') || this.data.profile
    const tasks = repository.getAll()
    const date = new Date()
    const todayLabel = `${date.getMonth() + 1}月${date.getDate()}日`
    const pendingCount = tasks.filter(item => !item.done).length
    const visibleTasks = this.filterTasks(tasks, this.data.activeFilter)
    this.setData({ profile, tasks, visibleTasks, pendingCount, todayLabel,
      emptyTitle: this.data.activeFilter === 'done' ? '还没有已完成事项' : '还没有待办' })
  },

  filterTasks(tasks, filter) {
    if (filter === 'done') return tasks.filter(item => item.done)
    const open = tasks.filter(item => !item.done)
    if (filter === 'today') return open.filter(item => item.due && item.due.indexOf('今天') >= 0)
    if (filter === 'week') return open.filter(item => item.due && /今天|明天|周[一二三四五六日天]|本周/.test(item.due))
    return open
  },

  selectFilter(e) {
    const activeFilter = e.currentTarget.dataset.key
    this.setData({ activeFilter, visibleTasks: this.filterTasks(this.data.tasks, activeFilter),
      emptyTitle: activeFilter === 'done' ? '还没有已完成事项' : '还没有待办' })
  },

  startAdd() { this.setData({ showForm: true, draftTitle: '', draftDue: '' }) },
  cancelAdd() { this.setData({ showForm: false }) },
  onTitleInput(e) { this.setData({ draftTitle: e.detail.value }) },
  onDueInput(e) { this.setData({ draftDue: e.detail.value }) },

  saveTask() {
    const title = this.data.draftTitle.trim()
    if (!title) return wx.showToast({ title: '先填写待办内容', icon: 'none' })
    repository.add({ title, due: this.data.draftDue.trim() || '未注明时间' })
    this.setData({ showForm: false, activeFilter: 'all' })
    this.refresh()
  },

  toggleTask(e) {
    const id = e.currentTarget.dataset.id
    const item = repository.getAll().find(task => task.id === id)
    if (!item) return
    repository.update(id, { done: !item.done, completedAt: item.done ? null : Date.now() })
    this.refresh()
    wx.showToast({ title: item.done ? '已恢复待办' : '已完成', icon: 'success' })
  },

  openDetail(e) {
    wx.navigateTo({ url: `/pages/detail/index?id=${encodeURIComponent(e.currentTarget.dataset.id)}` })
  },

  openSettings() { wx.navigateTo({ url: '/pages/settings/index' }) }
})


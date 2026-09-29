Page({
  data: { childName: '孩子', groupName: '班级群' },
  onShow() {
    const profile = getApp().globalData.profile || wx.getStorageSync('banwu.profile') || {}
    this.setData({ childName: profile.childName || '孩子', groupName: profile.groupName || '班级群' })
  },
  onChildInput(e) { this.setData({ childName: e.detail.value }) },
  onGroupInput(e) { this.setData({ groupName: e.detail.value }) },
  save() {
    const profile = { childName: this.data.childName.trim(), groupName: this.data.groupName.trim() }
    wx.setStorageSync('banwu.profile', profile)
    getApp().globalData.profile = profile
    wx.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => wx.navigateBack(), 350)
  }
})


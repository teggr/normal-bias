// Clear through the actual editor; the category-row toggle controls visibility.
exports.clear = async function(page, click) {
    if (!await page.getByRole('textbox').count()) await click('Show search');
    const box = await page.getByRole('textbox').first().boundingBox();
    await page.mouse.click(box.x + 20, box.y + box.height / 2);
    const editor = page.locator('input:focus,textarea:focus');
    await editor.waitFor();
    await editor.fill('');
    await page.keyboard.press('Tab');
    await click('Search');
};

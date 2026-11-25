// Clear through the actual editor; the category-row toggle controls visibility.
exports.clear = async function(page, click) {
    if (await page.getByRole('button', {name:'Show search', exact:true}).count()) await click('Show search');
    // Wide layouts also contain the player's read-only status textbox.
    const field = page.locator('div[role="textbox"][aria-readonly="false"]').first();
    await field.waitFor();
    const box = await field.boundingBox();
    await page.mouse.click(box.x + 20, box.y + box.height / 2);
    const editor = page.locator('input:not([readonly]):focus,textarea:not([readonly]):focus');
    await editor.waitFor();
    await editor.fill('');
    await page.keyboard.press('Tab');
    await click('Search');
};

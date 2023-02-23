/*
    For cases whereby a page has both a submit and a cancel button, and the cancel button is an anchor link.
    To prevent the user from clicking submit, and then clicking cancel.

    <a data-button="submit-disabled"> ...
*/

;(function preventSubmitAfterCancel() {
  const disableOnSubmit = document.querySelectorAll('[data-button=submit-disabled]')[0]
  if (disableOnSubmit) {
    disableOnSubmit.addEventListener('click', function(e) {
      disableOnSubmit.setAttribute('disabled', 'disabled')
      const otherButtons = document.querySelectorAll('[type="submit"]')
      var b;
      if (otherButtons) {
        for(var b = 0; b < otherButtons.length; b++) {
          otherButtons[i].setAttribute('disabled', 'disabled')
        }
      }
    })
  }
})();

;(function preventCancelAfterSubmit() {
  const disableOnSubmit = document.querySelectorAll('[data-button=submit-disabled]')[0]
  const form = document.querySelector('form')
  if (form && disableOnSubmit) {
    disableOnSubmit.setAttribute('disabled', 'disabled')
  }
})();




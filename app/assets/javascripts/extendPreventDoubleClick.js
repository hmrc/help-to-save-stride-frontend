/*
    For cases whereby a page has both a submit and a cancel button, and the cancel button is an anchor link.
    To prevent the user from clicking submit, and then clicking cancel.

    <a data-button="submit-disabled"> ...
*/

;(function preventSubmitAfterCancel() {
  const form = document.querySelector('form')
  if (form) {
    const disableOnSubmit = document.querySelectorAll('[role*="button"]')[0]
    if (disableOnSubmit) {
      disableOnSubmit.addEventListener('click', function(e) {
        disableOnSubmit.setAttribute('disabled', 'disabled')
      })
    }
  }

})();


;(function preventCancelAfterSubmit() {
  const form = document.querySelector('form')
  if (form) {
    const disableOnSubmit = document.querySelectorAll('[data-button=submit-disabled]')[0]
    form.addEventListener('submit', function (e) {
      disableOnSubmit.setAttribute('disabled', 'disabled')
    })
  }
  /**
  * On form submit, prevent cancel button click.
  * A module within ASSETS_FRONTEND prevents the double click of the submit button:
  * https://github.com/hmrc/assets-frontend/blob/master/assets/javascripts/modules/preventDoubleSubmit.js
  */

})();



